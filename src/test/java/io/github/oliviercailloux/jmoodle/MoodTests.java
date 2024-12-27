package io.github.oliviercailloux.jmoodle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.jmoodle.raw.Feedback;
import io.github.oliviercailloux.jmoodle.raw.Moodle;
import io.github.oliviercailloux.jmoodle.raw.MoodleTestHelper;
import io.github.oliviercailloux.jmoodle.raw.SaveGrade;
import jakarta.json.JsonObject;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MoodTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(MoodTests.class);
  
  private static final URI MOODLE_PSL_TEST_SERVER =
      URI.create("https://moodle-test.psl.eu/webservice/rest/server.php");

  @Test
  void testSend() throws Exception {
    final Moodle moodle = Moodle.instance(MOODLE_PSL_TEST_SERVER);
    final Mood mood = Mood.using(moodle);

    DomHelper domHelper = DomHelper.domHelper();
    Document html = domHelper.html();
    Element body = html.createElement("body");
    html.getDocumentElement().appendChild(body);
    Element h1 = html.createElement("h1");
    h1.setTextContent("Start");
    body.appendChild(h1);
    Element p = html.createElement("p");
    p.setTextContent("Now this is feedback!");
    body.appendChild(p);
    mood.setGrades(9482, ImmutableSet.of(SaveGrade.overwriteLatestOrSet(73, 10d / 2d)
        .withFeedback(Feedback.html(domHelper.toString(html)))));
  }

  @Test
  void testServer() throws Exception {
    // Moodle moodle = Moodle.instance(MOODLE_PSL_TEST_SERVER);
    Moodle moodle = MoodleTestHelper.dumping(MOODLE_PSL_TEST_SERVER);
    final Mood mood = Mood.using(moodle);

    ImmutableSet<JsonObject> pluginsSet = mood.plugins();
    assertTrue(pluginsSet.size() >= 1);
    // JsonObject o = moodle.core_course_get_course_module(574852);
    // LOGGER.info("Module: {}", o);
    int courseId = mood.courseId("23_CIP_test_autograder");
    assertEquals(24705, courseId);
    ImmutableSet<JsonObject> gradableUsers = mood.gradableUsers(courseId);
    assertTrue(gradableUsers.size() >= 1);
    LOGGER.info("Users: {}", gradableUsers);
    mood.ignoreWarnings(true);
    ImmutableSet<Integer> assignmentIds = mood.assignmentIds(courseId);
    mood.ignoreWarnings(false);
    LOGGER.info("Assignments: {}", assignmentIds);
    assertTrue(assignmentIds.contains(9482));
    ImmutableTable<UserId, Integer, Double> gradesByAttempt = mood.gradesByAttempt(9482);
    assertTrue(gradesByAttempt.size() >= 1);
    LOGGER.info("Grades by attempt: {}", gradesByAttempt);
    Table<UserId, Integer, UserGradeFeedback> gradesByAssignment = mood.gradesByAssignment(courseId);
    assertTrue(gradesByAssignment.size() >= 1);
    LOGGER.info("Grades by assignment: {}", gradesByAssignment);
  }
  
}
