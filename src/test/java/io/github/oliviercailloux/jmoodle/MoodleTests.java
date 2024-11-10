package io.github.oliviercailloux.jmoodle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import jakarta.json.JsonObject;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MoodleTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(MoodleTests.class);

  private static record User (String name, int age) {
  }

  private static record Property (User user, String owner) {
  }

  private static final URI MOODLE_PSL_TEST_SERVER =
      URI.create("https://moodle-test.psl.eu/webservice/rest/server.php");

  @Test
  void testIdentity() throws Exception {
    ImmutableMap<String, String> solved = Moodle.solve(ImmutableMap.of("p1", "v1", "p2", "v2"));
    assertEquals(ImmutableMap.of("p1", "v1", "p2", "v2"), solved);
  }

  @Test
  void testInts() throws Exception {
    ImmutableMap<String, String> solved = Moodle.solve(ImmutableMap.of("p1", 1, "p2", 2));
    assertEquals(ImmutableMap.of("p1", "1", "p2", "2"), solved);
  }

  @Test
  void testList() throws Exception {
    ImmutableMap<String, String> solved =
        Moodle.solve(ImmutableMap.of("p1", ImmutableList.of("v1", "v2")));
    assertEquals(ImmutableMap.of("p1[0]", "v1", "p1[1]", "v2"), solved);
  }

  @Test
  void testRecord() throws Exception {
    ImmutableMap<String, String> solved =
        Moodle.solve(ImmutableMap.of("p1", new User("her name", 60)));
    assertEquals(ImmutableMap.of("p1[name]", "her name", "p1[age]", "60"), solved);
  }

  @Test
  void testSubRecord() throws Exception {
    ImmutableMap<String, String> solved =
        Moodle.solve(ImmutableMap.of("p1", new Property(new User("her name", 60), "the owner")));
    assertEquals(ImmutableMap.of("p1[user][name]", "her name", "p1[user][age]", "60", "p1[owner]",
        "the owner"), solved);
  }

  @Test
  void testEmbedded() throws Exception {
    ImmutableMap<String,
        String> solved = Moodle.solve(ImmutableMap.of("p1",
            ImmutableList.of(new Property(new User("her name", 60), "the owner"),
                new Property(new User("her name 2", 61), "the owner 2")), "notthere", Optional.empty()));
    assertEquals(ImmutableMap.of("p1[0][user][name]", "her name", "p1[0][user][age]", "60",
        "p1[0][owner]", "the owner", "p1[1][user][name]", "her name 2", "p1[1][user][age]", "61",
        "p1[1][owner]", "the owner 2"), solved);
  }

  @Test
  void testSend() throws Exception {
    Moodle moodle = Moodle.instance(MOODLE_PSL_TEST_SERVER);
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
    moodle.setGrades(9476, ImmutableList.of(MoodleSendGrade.overwriteLatestOrSet(73, 10d/3d).withFeedback(MoodleAssignFeedback.html(domHelper.toString(html)))));
  }

  @Test
  void testServer() throws Exception {
    Moodle moodle = Moodle.instance(MOODLE_PSL_TEST_SERVER);
    // moodle.dump = true;
    ImmutableSet<JsonObject> pluginsSet = moodle.jsonPlugins();
    assertTrue(pluginsSet.size() >= 1);

    int courseId = moodle.courseId("23_CIP_test_autograder");
    assertEquals(24705, courseId);
    ImmutableSet<Integer> assignmentIds = moodle.assignmentIds(courseId);
    LOGGER.info("Assignments: {}", assignmentIds);
    assertTrue(assignmentIds.contains(9476));
    ImmutableMap<Integer, Integer> latestAttempts = moodle.latestAttempts(9476);
    assertTrue(latestAttempts.size() >= 1);
    assertEquals(14, latestAttempts.get(73));
    ImmutableMap<Integer, Double> grades = moodle.grades(9476);
    assertTrue(grades.size() >= 1);
    assertEquals(3.33333d, grades.get(73));
  }
}
