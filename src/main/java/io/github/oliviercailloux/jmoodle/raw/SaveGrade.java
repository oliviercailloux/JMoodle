package io.github.oliviercailloux.jmoodle.raw;

import java.util.Optional;

public record SaveGrade (int userid, double grade, int attemptnumber, boolean addattempt,
    String workflowstate, Optional<GradePluginData> plugindata, Optional<AdvancedGradingData> advancedgradingdata) {

  /**
   * Will overwrite the attempt number if it exists, otherwise add a new attempt
   *
   * @param userid
   * @param grade
   * @param attemptnumber -1 for overwrite latest, otherwise, for a given number 0 ≤ x, the gui will
   *        show “tentative x+1” (as the gui is 1-based); even if not consecutive (can have attempts
   *        3 and 7, say), though non consecutive attempts are probably begging for trouble as it does not seem that the gui is designed for this use case.
   * @return the send grade object
   */
  public static SaveGrade overwriteOrAdd(int userid, double grade, int attemptnumber) {
    return new SaveGrade(userid, grade, attemptnumber, true, "graded", Optional.empty(), Optional.empty());
  }

  /** If no evaluation, sets the evaluation; if has some, overwrites the last one. */
  public static SaveGrade overwriteLatestOrSet(int userid, double grade) {
    return new SaveGrade(userid, grade, -1, true, "graded", Optional.empty(), Optional.empty());
  }

  public SaveGrade withFeedback(Feedback feedback) {
    return new SaveGrade(userid, grade, attemptnumber, addattempt, workflowstate,
        Optional.of(new GradePluginData(Optional.of(feedback), Optional.empty())), Optional.empty());
  }
}
