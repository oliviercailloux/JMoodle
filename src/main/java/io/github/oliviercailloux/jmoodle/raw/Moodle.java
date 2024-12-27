package io.github.oliviercailloux.jmoodle.raw;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.jaris.credentials.CredentialsReader;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Systematic port of a few moodle APIs. The method and parameter names are kept, we Optional<?> for
 * optional parameters except when it’s a collection or a string, in which case emptiness corresponds to missing.
 */
public class Moodle {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Moodle.class);

  private static ImmutableMap<String, Object> prefixes(Object o) {
    if (o instanceof List<?> || o instanceof Set<?>) {
      Collection<?> coll = (Collection<?>) o;
      ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
      Iterator<?> it = coll.iterator();
      int i = 0;
      while (it.hasNext()) {
        builder.put("[" + i + "]", it.next());
        ++i;
      }
      verify(i == coll.size());
      return builder.build();
    }
    if (o instanceof Record r) {
      RecordComponent[] comps = r.getClass().getRecordComponents();
      Stream<RecordComponent> components = Arrays.stream(comps);
      Function<RecordComponent, Object> getOrThrow = c -> {
        try {
          return c.getAccessor().invoke(r);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new IllegalArgumentException(e);
        }
      };
      return components
          .collect(ImmutableMap.toImmutableMap(c -> "[" + c.getName() + "]", getOrThrow));
    }
    return ImmutableMap.of("", o);
  }

  static ImmutableMap<String, String> solve(Map<String, ?> parameters) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    for (Map.Entry<String, ?> entry : parameters.entrySet()) {
      String parameterName = entry.getKey();
      Object possiblyOptionalValue = entry.getValue();
      final Object value;
      if (possiblyOptionalValue instanceof Optional<?> o) {
        if (o.isPresent()) {
          value = o.get();
        } else {
          LOGGER.debug("Optional parameter {} is empty, not serializing it.", parameterName);
          continue;
        }
      } else {
        value = possiblyOptionalValue;
      }
      checkArgument(!(value instanceof Optional<?>));
      if (value instanceof List<?> || value instanceof Set<?> || value instanceof Record) {
        ImmutableMap<String, Object> prefixedParams = prefixes(value);
        ImmutableMap<String, String> solved = solve(prefixedParams);
        for (Map.Entry<String, String> entry2 : solved.entrySet()) {
          builder.put(parameterName + entry2.getKey(), entry2.getValue());
        }
      } else if (value instanceof Format f) {
        builder.put(parameterName, Integer.toString(f.value()));
      } else if (value instanceof Instant i) {
        builder.put(parameterName, Long.toString(i.getEpochSecond()));
      } else if (value instanceof Boolean b) {
        builder.put(parameterName, b ? "1" : "0");
      } else {
        builder.put(parameterName, value.toString());
      }
    }
    return builder.build();
  }

  public static Moodle instance(URI moodleServer) {
    return new Moodle(moodleServer, CredentialsReader.keyReader().getCredentials().API_KEY(),
        ClientBuilder.newClient(), Json.createReaderFactory(ImmutableMap.of()));
  }

  public static Moodle instance(URI moodleServer, String apiKey, Client client,
      JsonReaderFactory jsonReaderFactory) {
    return new Moodle(moodleServer, apiKey, client, jsonReaderFactory);
  }

  private final URI moodleServer;
  private final String apiKey;
  private final Client client;
  private final JsonReaderFactory jsonReaderFactory;
  boolean dump;

  private Moodle(URI moodleServer, String apiKey, Client client,
      JsonReaderFactory jsonReaderFactory) {
    this.moodleServer = moodleServer;
    this.apiKey = apiKey;
    this.client = client;
    this.jsonReaderFactory = jsonReaderFactory;
    this.dump = false;
  }

  private JsonObject parse(String jsonAnswer) {
    checkNotNull(jsonAnswer);
    LOGGER.debug("Json answer: {}.", jsonAnswer);
    JsonObject full;
    try (JsonReader jr = jsonReaderFactory.createReader(new StringReader(jsonAnswer))) {
      full = jr.readObject();
    }
    if (dump) {
      try (Writer w = Files.newBufferedWriter(Path.of("answer.json"))) {
        Json.createWriterFactory(ImmutableMap.of(JsonGenerator.PRETTY_PRINTING, true))
            .createWriter(w).write(full);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    checkState(full.containsKey("warnings"), full);
    Set<String> keys = new LinkedHashSet<>(full.keySet());
    checkState(keys.size() == 2, keys);
    return full;
  }

  /** Iff the answer is null, returns null. */
  public JsonObject send(String wsFunction, Map<String, ?> parameters) {
    UriBuilder uriBuilder = UriBuilder.fromUri(moodleServer);
    uriBuilder.queryParam("moodlewsrestformat", "json");
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", wsFunction);
    ImmutableMap<String, String> solved = solve(parameters);
    for (Map.Entry<String, String> entry : solved.entrySet()) {
      uriBuilder.queryParam(entry.getKey(), entry.getValue());
    }

    String answer = client.target(uriBuilder).request().get(String.class);
    if (answer.equals("null")) {
      return null;
    }
    JsonObject full = parse(answer);
    return full;
  }

  public JsonObject tool_mobile_get_plugins_supporting_mobile() {
    return send("tool_mobile_get_plugins_supporting_mobile", ImmutableMap.of());
  }
  
  public JsonObject core_course_get_course_module(int cmid) {
    return send("core_course_get_course_module", ImmutableMap.of("cmid", cmid));
  }
  
  public JsonObject core_course_get_courses_by_field(String field, String value) {
    ImmutableMap<String, String> parameters = ImmutableMap.of("field", field, "value", value);
    return send("core_course_get_courses_by_field", parameters);
  }

  /**
   * If empty return all courses except front page course.
   */
  public JsonObject core_course_get_courses(Set<Integer> ids) {
    ImmutableMap<String, ?> parameters = ImmutableMap.of("ids", ids);
    return send("core_course_get_courses", parameters);
  }

  public JsonObject mod_assign_get_assignments(Set<Integer> courseids, Set<String> capabilities, Optional<Boolean> includenotenrolledcourses) {
    ImmutableMap<String, ?> parameters =
        ImmutableMap.of("courseids", courseids, "capabilities", capabilities, "includenotenrolledcourses", includenotenrolledcourses);
    return send("mod_assign_get_assignments", parameters);
  }

  public JsonObject core_grades_get_gradable_users(int courseid, Optional<Integer> groupid, Optional<Boolean> onlyactive) {
    ImmutableMap<String, ?> parameters =
        ImmutableMap.of("courseid", courseid, "groupid", groupid, "onlyactive", onlyactive);
    return send("core_grades_get_gradable_users", parameters);
  }

  public JsonObject mod_assign_get_submissions(Set<Integer> assignmentids, String status, Optional<Instant> since, Optional<Instant> before) {
    ImmutableMap<String, ?> parameters =
        ImmutableMap.of("assignmentids", assignmentids, "status", status, "since", since, "before", before);
    return send("mod_assign_get_submissions", parameters);
  }

  public JsonObject gradereport_user_get_grade_items(int courseid, Optional<Integer> userid,
      Optional<Integer> groupid) {
    ImmutableMap<String, ?> parameters =
        ImmutableMap.of("courseid", courseid, "userid", userid, "groupid", groupid);
    return send("gradereport_user_get_grade_items", parameters);
  }

  public JsonObject mod_assign_get_grades(Set<Integer> assignmentids, Optional<Instant> since) {
    ImmutableMap<String, ?> parameters =
        ImmutableMap.of("assignmentids", assignmentids, "since", since);
    return send("mod_assign_get_grades", parameters);
  }

  public void mod_assign_save_grade(int assignmentid, int userid, double grade,
      int attemptnumber, boolean addattempt, String workflowstate, boolean applytoall,
      Optional<GradePluginData> plugindata, Optional<AdvancedGradingData> advancedgradingdata) {
    ImmutableMap.Builder<String, Object> parametersBuilder = new ImmutableMap.Builder<>();
    parametersBuilder.put("assignmentid", assignmentid);
    parametersBuilder.put("userid", userid);
    parametersBuilder.put("grade", grade);
    parametersBuilder.put("attemptnumber", attemptnumber);
    parametersBuilder.put("addattempt", addattempt);
    parametersBuilder.put("workflowstate", workflowstate);
    parametersBuilder.put("applytoall", applytoall);
    plugindata.ifPresent(pd -> parametersBuilder.put("plugindata", pd));
    advancedgradingdata.ifPresent(agd -> parametersBuilder.put("advancedgradingdata", agd));
    ImmutableMap<String, ?> parameters = parametersBuilder.build();
    JsonObject full = send("mod_assign_save_grade", parameters);
    checkState(full == null);
  }
  
  public void mod_assign_save_grades(int assignmentid, boolean applytoall, Set<SaveGrade> grades) {
    ImmutableMap<String, ?> parameters =
    ImmutableMap.of("assignmentid", assignmentid, "applytoall", applytoall, "grades", grades);
    JsonObject full = send("mod_assign_save_grades", parameters);
    checkState(full == null);
  }
}
