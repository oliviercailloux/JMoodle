package io.github.oliviercailloux.jmoodle;

public record MoodleAssignFeedback(String text, /** 1 = HTML, 0 = MOODLE, 2 = PLAIN or 4 = MARKDOWN */ int format) {
  
}
