package io.github.oliviercailloux.jmoodle.raw;

public record Feedback (String text, Format format) {
  public static Feedback html(String serialized) {
    return new Feedback(serialized, Format.HTML);
  }

  public static Feedback moodle(String text) {
    return new Feedback(text, Format.MOODLE);
  }

  public static Feedback plain(String text) {
    return new Feedback(text, Format.PLAIN);
  }

  public static Feedback markdown(String text) {
    return new Feedback(text, Format.MARKDOWN);
  }
}
