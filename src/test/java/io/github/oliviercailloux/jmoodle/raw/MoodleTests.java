package io.github.oliviercailloux.jmoodle.raw;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoodleTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(MoodleTests.class);

  private static record User (String name, int age) {
  }

  private static record UserOpt (String name, Optional<Integer> age) {
  }

  private static record Property (User user, String owner) {
  }

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
  void testVarious() throws Exception {
    ImmutableMap<String, ?> parameters = ImmutableMap.of("courseids", ImmutableSet.of(4010),
        "capabilities", ImmutableSet.of(), "includenotenrolledcourses", Optional.of(true));
    ImmutableMap<String, String> solved = Moodle.solve(parameters);
    assertEquals(ImmutableMap.of("courseids[0]", "4010", "includenotenrolledcourses", "1"), solved);
  }

  @Test
  void testSomeMissing() throws Exception {
    ImmutableMap<String, String> solved =
        Moodle.solve(ImmutableMap.of("p1", 1, "p2", Optional.empty()));
    assertEquals(ImmutableMap.of("p1", "1"), solved);
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
  void testOptionalRecord() throws Exception {
    ImmutableMap<String, String> solved =
        Moodle.solve(ImmutableMap.of("p1", new UserOpt("her name", Optional.of(60))));
    assertEquals(ImmutableMap.of("p1[name]", "her name", "p1[age]", "60"), solved);
  }

  @Test
  void testPartialRecord() throws Exception {
    ImmutableMap<String, String> solved =
        Moodle.solve(ImmutableMap.of("p1", new UserOpt("her name", Optional.empty())));
    assertEquals(ImmutableMap.of("p1[name]", "her name"), solved);
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
                new Property(new User("her name 2", 61), "the owner 2")),
            "notthere", Optional.empty()));
    assertEquals(ImmutableMap.of("p1[0][user][name]", "her name", "p1[0][user][age]", "60",
        "p1[0][owner]", "the owner", "p1[1][user][name]", "her name 2", "p1[1][user][age]", "61",
        "p1[1][owner]", "the owner 2"), solved);
  }
}
