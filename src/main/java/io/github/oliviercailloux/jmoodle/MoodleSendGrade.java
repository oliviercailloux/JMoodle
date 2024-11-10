package io.github.oliviercailloux.jmoodle;

public record MoodleSendGrade (int userid, double grade, /** -1 for last attempt, otherwise appears as “tentative x+1”, date stays the original date of that attempt if there was one, otherwise it is created with a current timestamp */ int attemptnumber, int addattempt, String workflowstate, MoodleSendGradePluginData plugindata) {
  
  /**
   * Will overwrite the attempt number if it exists, otherwise add a new attempt
   * @param userid
   * @param grade
   * @param attemptnumber -1 for overwrite latest, otherwise, for a given number 0 ≤ x, the gui will show “tentative x+1” (as the gui is 1-based); even if not consecutive (can have attempt 3 and 14)
   * @return the send grade object
   */
  public static MoodleSendGrade overwriteOrAdd(int userid, double grade, int attemptnumber) {
    return new MoodleSendGrade(userid, grade, attemptnumber, 0, "graded", new MoodleSendGradePluginData(new MoodleAssignFeedback("heh", 1)));
  }

  /** If no evaluation, sets the evaluation; if has some, overwrites the last one. */
  public static MoodleSendGrade overwriteLatestOrSet(int userid, double grade) {
    return new MoodleSendGrade(userid, grade, -1, 0, "graded", new MoodleSendGradePluginData(new MoodleAssignFeedback("heh", 1)));
  }
}
