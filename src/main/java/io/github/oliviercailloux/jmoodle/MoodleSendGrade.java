package io.github.oliviercailloux.jmoodle;

import java.util.Optional;

public record MoodleSendGrade (int userid, double grade, int attemptnumber, int addattempt,
    String workflowstate, Optional<MoodleSendGradePluginData> plugindata) {

  /**
   * Will overwrite the attempt number if it exists, otherwise add a new attempt
   *
   * @param userid
   * @param grade
   * @param attemptnumber -1 for overwrite latest, otherwise, for a given number 0 ≤ x, the gui will
   *        show “tentative x+1” (as the gui is 1-based); even if not consecutive (can have attempts
   *        3 and 7, say), though non consecutive attempts are probably begging for trouble and
   *        should be avoided IMHO; it does not seem that the gui is designed for this use case.
   * @return the send grade object
   */
  public static MoodleSendGrade overwriteOrAdd(int userid, double grade, int attemptnumber) {
    return new MoodleSendGrade(userid, grade, attemptnumber, 0, "graded", Optional.empty());
  }

  /** If no evaluation, sets the evaluation; if has some, overwrites the last one. */
  public static MoodleSendGrade overwriteLatestOrSet(int userid, double grade) {
    return new MoodleSendGrade(userid, grade, -1, 0, "graded", Optional.empty());
  }

  public MoodleSendGrade withFeedback(MoodleAssignFeedback feedback) {
    return new MoodleSendGrade(userid, grade, attemptnumber, addattempt, workflowstate,
        Optional.of(new MoodleSendGradePluginData(feedback)));
  }
}
