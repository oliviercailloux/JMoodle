package io.github.oliviercailloux.jmoodle;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.jaris.credentials.CredentialsReader;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
import org.w3c.dom.Element;

public class Moodle {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Moodle.class);

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

  private ImmutableSet<JsonObject> parse(String jsonAnswer) {
    checkNotNull(jsonAnswer);
    LOGGER.debug("Json answer: {}.", jsonAnswer);
    if (dump) {
      try {
        Files.writeString(Path.of("answer.json"), jsonAnswer);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    JsonObject json;
    try (JsonReader jr = jsonReaderFactory.createReader(new StringReader(jsonAnswer))) {
      json = jr.readObject();
    }
    checkState(json.containsKey("warnings"), json);
    JsonArray warnings = json.getJsonArray("warnings");
    checkState(warnings.isEmpty(), warnings);
    Set<String> keys = new LinkedHashSet<>(json.keySet());
    checkState(keys.size() == 2, keys);
    keys.remove("warnings");
    checkState(keys.size() == 1, keys);
    String key = Iterables.getOnlyElement(keys);
    JsonArray jsonMainArray = json.getJsonArray(key);
    ImmutableSet<JsonObject> jsonElems =
        jsonMainArray.stream().map(v -> (JsonObject) v).collect(ImmutableSet.toImmutableSet());
    return jsonElems;
  }

  private static ImmutableMap<String, Object> prefixes(Object o) {
    if (o instanceof List<?> l) {
      return IntStream.range(0, l.size()).boxed()
          .collect(ImmutableMap.toImmutableMap(i -> "[" + i + "]", l::get));
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
      if (value instanceof List<?> || value instanceof Record) {
        ImmutableMap<String, Object> prefixedParams = prefixes(value);
        ImmutableMap<String, String> solved = solve(prefixedParams);
        for (Map.Entry<String, String> entry2 : solved.entrySet()) {
          builder.put(parameterName + entry2.getKey(), entry2.getValue());
        }
      } else {
        builder.put(parameterName, value.toString());
      }
    }
    return builder.build();
  }

  /** Iff the answer is null, returns null. */
  private ImmutableSet<JsonObject> send(String wsFunction, Map<String, ?> parameters) {
    UriBuilder uriBuilder = UriBuilder.fromUri(moodleServer);
    uriBuilder.queryParam("moodlewsrestformat", "json");
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", wsFunction);
    ImmutableMap<String, String> solved = solve(parameters);
    for (Map.Entry<String, String> entry : solved.entrySet()) {
      uriBuilder.queryParam(entry.getKey(), entry.getValue());
    }

    String answer = client.target(uriBuilder).request().get(String.class);
    if (answer.equals("null"))
      return null;
    return parse(answer);
  }

  public ImmutableSet<JsonObject> coursesByField(String field, String value) {
    String wsFunction = "core_course_get_courses_by_field";
    Map<String, String> parameters = Map.of("field", field, "value", value);
    return send(wsFunction, parameters);
  }

  public int courseId(String shortname) {
    JsonObject course = Iterables.getOnlyElement(coursesByField("shortname", shortname));
    checkState(course.containsKey("id"));
    int courseId = course.getInt("id");
    return courseId;
  }

  public ImmutableSet<JsonObject> jsonPlugins() {
    return send("tool_mobile_get_plugins_supporting_mobile", ImmutableMap.of());
  }

  public ImmutableSet<Integer> assignmentIds(int courseId) {
    ImmutableSet<JsonObject> jsons = send("mod_assign_get_assignments",
        ImmutableMap.of("courseids", ImmutableList.of(String.valueOf(courseId))));
    checkState(jsons.size() == 1);
    JsonObject course = Iterables.getOnlyElement(jsons);
    checkState(course.containsKey("id"));
    checkState(course.getInt("id") == courseId);
    JsonArray assignmentsArray = course.getJsonArray("assignments");

    final ImmutableSet.Builder<Integer> idsBuilder = new ImmutableSet.Builder<>();
    for (JsonValue jsonValue : assignmentsArray) {
      JsonObject assignment = (JsonObject) jsonValue;
      checkState(assignment.containsKey("id"));
      idsBuilder.add(assignment.getInt("id"));
    }
    ImmutableSet<Integer> ids = idsBuilder.build();
    checkState(ids.size() == assignmentsArray.size());
    return ids;
  }

  /** https://stackoverflow.com/a/67127067/ */
  private static <T extends Record> Constructor<T> canonicalConstructor(Class<T> recordClass) {
    Class<?>[] componentTypes = Arrays.stream(recordClass.getRecordComponents())
        .map(rc -> rc.getType()).toArray(Class<?>[]::new);
    try {
      return recordClass.getDeclaredConstructor(componentTypes);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new VerifyException(e);
    }
  }

  static <T extends Record> T asRecord(JsonObject json, Class<T> recordClass) {
    final ImmutableList.Builder<Object> canonicalParamsBuilder = new ImmutableList.Builder<>();

    RecordComponent[] recordComponents = recordClass.getRecordComponents();
    for (RecordComponent recordComponent : recordComponents) {
      String name = recordComponent.getName();
      Class<?> type = recordComponent.getType();
      checkState(json.containsKey(name), json);
      JsonValue jsonValue = json.get(name);
      Object wrapped;
      if (jsonValue.equals(JsonValue.TRUE)) {
        checkArgument(type == boolean.class);
        wrapped = true;
      } else if (jsonValue.equals(JsonValue.FALSE)) {
        checkArgument(type == boolean.class);
        wrapped = false;
      } else if (jsonValue instanceof JsonString) {
        checkArgument(type == String.class);
        wrapped = ((JsonString) jsonValue).getString();
      } else if (jsonValue instanceof JsonNumber) {
        checkArgument(type == double.class || type == int.class, name);
        if (type == int.class) {
          wrapped = ((JsonNumber) jsonValue).intValue();
        } else {
          wrapped = ((JsonNumber) jsonValue).doubleValue();
        }
      } else {
        throw new IllegalArgumentException("Unexpected JSON value: " + jsonValue);
      }
      canonicalParamsBuilder.add(wrapped);
    }
    ImmutableList<Object> canonicalParams = canonicalParamsBuilder.build();
    Constructor<T> canonicalConstructor = canonicalConstructor(recordClass);
    T newInstance;
    try {
      newInstance = canonicalConstructor.newInstance(canonicalParams.toArray());
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException e) {
      throw new VerifyException(e);
    }
    return newInstance;
  }

  public ImmutableMap<Integer, Double> grades(int assignmentId) {
    ImmutableSet<JsonObject> jsons = send("mod_assign_get_grades",
        ImmutableMap.of("assignmentids", ImmutableList.of(String.valueOf(assignmentId))));
    checkState(jsons.size() == 1);
    JsonObject assignment = Iterables.getOnlyElement(jsons);
    checkState(assignment.containsKey("assignmentid"), assignment);
    checkState(assignment.getInt("assignmentid") == assignmentId);
    JsonArray gradesArray = assignment.getJsonArray("grades");
    ImmutableSet<MoodleReadGrade> grades = gradesArray.stream().map(g -> (JsonObject) g)
        .map(o -> asRecord(o, MoodleReadGrade.class)).collect(ImmutableSet.toImmutableSet());

    return grades.stream().collect(
        ImmutableMap.toImmutableMap(MoodleReadGrade::userid, MoodleReadGrade::gradeAsDouble));
  }

  public ImmutableMap<Integer, Integer> latestAttempts(int assignmentId) {
    ImmutableSet<JsonObject> jsons = send("mod_assign_get_submissions",
        ImmutableMap.of("assignmentids", ImmutableList.of(String.valueOf(assignmentId))));
    checkState(jsons.size() == 1);
    JsonObject assignment = Iterables.getOnlyElement(jsons);
    checkState(assignment.containsKey("assignmentid"), assignment);
    checkState(assignment.getInt("assignmentid") == assignmentId);
    JsonArray submissionsArray = assignment.getJsonArray("submissions");
    return submissionsArray.stream().map(g -> (JsonObject) g).collect(
        ImmutableMap.toImmutableMap(g -> g.getInt("userid"), g -> g.getInt("attemptnumber")));
  }

  public void setGrades(int assignmentId, List<MoodleSendGrade> grades) {
    ImmutableSet<JsonObject> jsons = send("mod_assign_save_grades",
        Map.of("assignmentid", String.valueOf(assignmentId), "applytoall", "0", "grades", grades));
    checkState(jsons == null, jsons);
  }
}
