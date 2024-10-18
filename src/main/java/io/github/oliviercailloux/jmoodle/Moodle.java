package io.github.oliviercailloux.jmoodle;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.credentials.CredentialsReader;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import io.github.oliviercailloux.jaris.throwing.TFunction;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
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
        ClientBuilder.newClient(), Json.createReaderFactory(ImmutableMap.of()), true);
  }

  private final URI moodleServer;
  private final String apiKey;
  private final Client client;
  private final JsonReaderFactory jsonReaderFactory;
  private final boolean dump;

  private Moodle(URI moodleServer, String apiKey, Client client,
      JsonReaderFactory jsonReaderFactory, boolean dump) {
    this.moodleServer = moodleServer;
    this.apiKey = apiKey;
    this.client = client;
    this.jsonReaderFactory = jsonReaderFactory;
    this.dump = dump;
  }

  private ImmutableSet<JsonObject> parse(String jsonAnswer) {
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
    checkState(json.containsKey("warnings"));
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

  private String queryWithRecords(String wsFunction, Map<String, List<Object>> parameters,
      String format) {
    final ImmutableMap.Builder<String, String> allParams = new ImmutableMap.Builder<>();

    for (Map.Entry<String, List<Object>> entry : parameters.entrySet()) {
      String parameterName = entry.getKey();
      List<Object> records = entry.getValue();
      for (int i = 0; i < parameters.size(); i++) {
        String prefix = parameterName + "[" + i + "]";
        Object record = records.get(i);
        Field[] declaredFields = record.getClass().getDeclaredFields();
        for (Field field : declaredFields) {
          String value;
          try {
            value = field.get(record).toString();
          } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
          }
          allParams.put(prefix + "[" + field.getName() + "]", value);
        }
      }
    }

    return send(wsFunction, allParams.build(), format);
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
      Object value = entry.getValue();
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

  private String send(String wsFunction, Map<String, ?> parameters, String format) {
    UriBuilder uriBuilder = UriBuilder.fromUri(moodleServer);
    uriBuilder.queryParam("moodlewsrestformat", format);
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", wsFunction);
    ImmutableMap<String, String> solved = solve(parameters);
    for (Map.Entry<String, String> entry : solved.entrySet()) {
      uriBuilder.queryParam(entry.getKey(), entry.getValue());
    }

    String answer = client.target(uriBuilder).request().get(String.class);
    return answer;
  }

  private String queryOld(String wsFunction, Map<String, String> parameters, String format) {
    UriBuilder uriBuilder = UriBuilder.fromUri(moodleServer);
    uriBuilder.queryParam("moodlewsrestformat", format);
    uriBuilder.queryParam("wstoken", apiKey);
    uriBuilder.queryParam("wsfunction", wsFunction);
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      uriBuilder.queryParam(entry.getKey(), entry.getValue());
    }
    String answer = client.target(uriBuilder).request().get(String.class);
    return answer;
  }

  private ImmutableSet<JsonObject> jsonQuery(String wsFunction, Map<String, ?> parameters) {
    String jsonAnswer = send(wsFunction, parameters, "json");
    return parse(jsonAnswer);
  }

  public ImmutableSet<JsonObject> coursesByField(String field, String value) {
    String wsFunction = "core_course_get_courses_by_field";
    Map<String, String> parameters = Map.of("field", field, "value", value);
    return jsonQuery(wsFunction, parameters);
  }

  public int courseId(String shortname) {
    JsonObject course = Iterables.getOnlyElement(coursesByField("shortname", shortname));
    checkState(course.containsKey("id"));
    int courseId = course.getInt("id");
    return courseId;
  }

  public ImmutableSet<JsonObject> jsonPlugins() {
    String jsonAnswer =
        send("tool_mobile_get_plugins_supporting_mobile", ImmutableMap.of(), "json");
    return parse(jsonAnswer);
  }

  public ImmutableSet<Integer> assignmentIds(int courseId) {
    String jsonAnswer = send("mod_assign_get_assignments",
        ImmutableMap.of("courseids[0]", String.valueOf(courseId)), "json");
    ImmutableSet<JsonObject> jsons = parse(jsonAnswer);
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

  public ImmutableSet<JsonObject> grades(int assignmentId) {
    String jsonAnswer = send("mod_assign_get_grades",
        ImmutableMap.of("assignmentids[0]", String.valueOf(assignmentId)), "json");
    ImmutableSet<JsonObject> jsons = parse(jsonAnswer);
    checkState(jsons.size() == 1);
    JsonObject assignment = Iterables.getOnlyElement(jsons);
    checkState(assignment.containsKey("assignmentid"), assignment);
    checkState(assignment.getInt("assignmentid") == assignmentId);
    JsonArray gradesArray = assignment.getJsonArray("grades");
    return gradesArray.stream().map(g -> (JsonObject) g).collect(ImmutableSet.toImmutableSet());
  }

  public void setGrades(int assignmentId, List<MoodleGrade> grades) {
    // https://moodle-test.psl.eu/webservice/rest/server.php?wstoken=02db21541b8b81ab2436e56034afbac7&wsfunction=mod_assign_save_grades&assignmentid=9476&applytoall=0&grades[0][userid]=73&grades[0][grade]=12&grades[0][attemptnumber]=-1&grades[0][addattempt]=0&grades[0][workflowstate]=graded
    // Fonctionne !
    String jsonAnswer = send("mod_assign_save_grades",
        Map.of("assignmentid", String.valueOf(assignmentId), "applytoall", "0", "grades", grades),
        "json");
    ImmutableSet<JsonObject> jsons = parse(jsonAnswer);
    checkState(jsons.size() == 1);
    JsonObject assignment = Iterables.getOnlyElement(jsons);
  }

  private void processXmlWarnings(Element warningsEl) {
    checkState(keyName(warningsEl).equals("warnings"));
    ImmutableList<Element> warningsChildren = DomHelper.toElements(warningsEl.getChildNodes());
    checkState(warningsChildren.size() == 1);
    Element warningsChild = warningsChildren.get(0);
    checkState(warningsChild.getTagName().equals("MULTIPLE"));
    String warningsText = warningsChild.getTextContent();
    if (!warningsText.isEmpty()) {
      LOGGER.warn(warningsText);
    }
  }

  private String keyName(Element element) {
    checkState(element.getTagName().equals("KEY"));
    String keyName = element.getAttribute("name");
    checkState(!keyName.isEmpty());
    return keyName;
  }
}
