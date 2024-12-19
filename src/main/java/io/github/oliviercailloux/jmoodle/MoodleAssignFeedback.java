package io.github.oliviercailloux.jmoodle;

import io.github.oliviercailloux.jaris.xml.DomHelper;
import org.w3c.dom.Document;

public record MoodleAssignFeedback (String text, int format) {
  public static MoodleAssignFeedback html(String serialized) {
    return new MoodleAssignFeedback(serialized, 1);
  }

  public static MoodleAssignFeedback moodle(String text) {
    return new MoodleAssignFeedback(text, 0);
  }

  public static MoodleAssignFeedback plain(String text) {
    return new MoodleAssignFeedback(text, 2);
  }

  public static MoodleAssignFeedback markdown(String text) {
    return new MoodleAssignFeedback(text, 4);
  }
}
