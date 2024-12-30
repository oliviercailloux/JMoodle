package io.github.oliviercailloux.jmoodle;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Table;
import io.github.oliviercailloux.jmoodle.raw.Feedback;
import io.github.oliviercailloux.jmoodle.raw.Format;
import io.github.oliviercailloux.jmoodle.raw.Moodle;
import io.github.oliviercailloux.jmoodle.raw.SaveGrade;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Mood {

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

  public static Mood using(Moodle moodle) {
    return new Mood(moodle);
  }

  private Moodle moodle;
  private boolean ignoreWarnings;

  private Mood(Moodle moodle) {
    this.moodle = moodle;
    this.ignoreWarnings = false;
  }

  public void ignoreWarnings(@SuppressWarnings("hiding") boolean ignoreWarnings) {
    this.ignoreWarnings = ignoreWarnings;
  }

  private ImmutableSet<JsonObject> main(JsonObject full) {
    checkState(full.containsKey("warnings"), full);
    Set<String> keys = new LinkedHashSet<>(full.keySet());
    checkState(keys.size() == 2, keys);
    JsonArray warnings = full.getJsonArray("warnings");
    if (!ignoreWarnings) {
      checkState(warnings.isEmpty(), warnings);
    }
    String otherKey =
        keys.stream().filter(k -> !k.equals("warnings")).collect(MoreCollectors.onlyElement());
    JsonArray jsonMainArray = full.getJsonArray(otherKey);
    ImmutableSet<JsonObject> jsonElems =
        jsonMainArray.stream().map(v -> (JsonObject) v).collect(ImmutableSet.toImmutableSet());
    return jsonElems;
  }

  public ImmutableSet<JsonObject> parse(String wsFunction, Map<String, String> parameters) {
    JsonObject full = moodle.send(wsFunction, parameters);
    return main(full);
  }

  public ImmutableSet<JsonObject> plugins() {
    JsonObject full = moodle.tool_mobile_get_plugins_supporting_mobile();
    return main(full);
  }

  private ImmutableSet<JsonObject> coursesByField(String field, String value) {
    JsonObject full = moodle.core_course_get_courses_by_field(field, value);
    return main(full);
  }

  public int courseId(String shortname) {
    ImmutableSet<JsonObject> courses = coursesByField("shortname", shortname);
    JsonObject course = Iterables.getOnlyElement(courses);
    checkState(course.containsKey("id"));
    int courseId = course.getInt("id");
    return courseId;
  }

  public ImmutableSet<JsonObject> gradableUsers(int courseId) {
    JsonObject full =
        moodle.core_grades_get_gradable_users(courseId, Optional.empty(), Optional.empty());
    return main(full);
  }

  public ImmutableSet<Integer> assignmentIds(int courseId) {
    JsonObject full = moodle.mod_assign_get_assignments(ImmutableSet.of(courseId),
        ImmutableSet.of(), Optional.empty());
    ImmutableSet<JsonObject> jsons = main(full);
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

  /**
   * Giving up, it seems that the teacher has an attempt number zero automatically.
   */
  ImmutableMap<UserId, Integer> latestAttempts(int assignmentId) {
    JsonObject full = moodle.mod_assign_get_submissions(ImmutableSet.of(assignmentId), "",
        Optional.empty(), Optional.empty());
    ImmutableSet<JsonObject> jsons = main(full);
    checkState(jsons.size() == 1);
    JsonObject assignment = Iterables.getOnlyElement(jsons);
    checkState(assignment.containsKey("assignmentid"), assignment);
    checkState(assignment.getInt("assignmentid") == assignmentId);
    JsonArray submissionsArray = assignment.getJsonArray("submissions");
    ImmutableSetMultimap<UserId,
        Integer> allAttempts = submissionsArray.stream().map(g -> (JsonObject) g).collect(
            ImmutableSetMultimap.toImmutableSetMultimap(g -> new UserId(g.getInt("userid")),
                g -> g.getInt("attemptnumber")));
    ImmutableMap.Builder<UserId, Integer> latestAttempts = ImmutableMap.builder();
    for (UserId userId : allAttempts.keySet()) {
      ImmutableSet<Integer> herAttempts = allAttempts.get(userId);
      int min = Collections.min(herAttempts);
      checkState(min == 1, herAttempts);
      int max = Collections.max(herAttempts);
      checkState(herAttempts.size() == max, herAttempts);
      latestAttempts.put(userId, max);
    }
    return latestAttempts.build();
  }

  public Table<UserId, Integer, UserGradeFeedback> gradesByAssignment(int courseId) {
    JsonObject full =
        moodle.gradereport_user_get_grade_items(courseId, Optional.empty(), Optional.empty());
    ImmutableSet<JsonObject> userGrades = main(full);
    ImmutableTable.Builder<UserId, Integer, UserGradeFeedback> gradesBuilder =
        ImmutableTable.builder();
    for (JsonObject userGrade : userGrades) {
      int userId = userGrade.getJsonNumber("userid").intValueExact();
      String userFullName = userGrade.getString("userfullname");
      JsonArray gradeItems = userGrade.getJsonArray("gradeitems");
      for (JsonValue gradeItemValue : gradeItems) {
        JsonObject gradeItem = (JsonObject) gradeItemValue;
        String itemType = gradeItem.getString("itemtype");
        JsonValue itemModule = gradeItem.get("itemmodule");
        JsonValue gradeRaw = gradeItem.get("graderaw");
        if (!itemType.equals("mod") || itemModule.equals(JsonValue.NULL)
            || !((JsonString) itemModule).getString().equals("assign")
            || gradeRaw.equals(JsonValue.NULL)) {
          continue;
        }
        int assignmentId = gradeItem.getInt("iteminstance");
        String assignmentName = gradeItem.getString("itemname");
        double grade = ((JsonNumber) gradeRaw).doubleValue();
        String feedbackStr = gradeItem.getString("feedback");
        int feedbackformat = gradeItem.getJsonNumber("feedbackformat").intValueExact();
        Feedback feedback = new Feedback(feedbackStr, Format.fromValue(feedbackformat));
        UserGradeFeedback userGradeFeedback = new UserGradeFeedback(new UserId(userId),
            userFullName, assignmentId, assignmentName, grade, feedback);
        gradesBuilder.put(new UserId(userId), assignmentId, userGradeFeedback);
      }
    }
    return gradesBuilder.build();
  }

  public ImmutableTable<UserId, Integer, Double> gradesByAttempt(int assignmentId) {
    JsonObject full = moodle.mod_assign_get_grades(ImmutableSet.of(assignmentId), Optional.empty());
    ImmutableSet<JsonObject> jsons = main(full);
    checkState(jsons.size() == 1);
    JsonObject assignment = Iterables.getOnlyElement(jsons);
    checkState(assignment.containsKey("assignmentid"), assignment);
    checkState(assignment.getInt("assignmentid") == assignmentId);
    JsonArray gradesArray = assignment.getJsonArray("grades");
    ImmutableTable.Builder<UserId, Integer, Double> gradesBuilder = ImmutableTable.builder();
    for (JsonValue gradeValue : gradesArray) {
      JsonObject gradeObject = (JsonObject) gradeValue;
      checkState(gradeObject.containsKey("userid"), gradeObject);
      checkState(gradeObject.containsKey("attemptnumber"), gradeObject);
      checkState(gradeObject.containsKey("grade"), gradeObject);
      UserId userId = new UserId(gradeObject.getInt("userid"));
      int attemptNumber = gradeObject.getInt("attemptnumber");
      String gradeStr = gradeObject.getString("grade");
      double grade = Double.parseDouble(gradeStr);
      gradesBuilder.put(userId, attemptNumber, grade);
    }
    return gradesBuilder.build();
  }

  public void setGrades(int assignmentId, Set<SaveGrade> grades) {
    moodle.mod_assign_save_grades(assignmentId, false, grades);
  }
}
