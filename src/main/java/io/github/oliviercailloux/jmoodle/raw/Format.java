package io.github.oliviercailloux.jmoodle.raw;

public enum Format {
  MOODLE(0), HTML(1), PLAIN(2), MARKDOWN(4);

  public static Format fromValue(int feedbackformat) {
    switch (feedbackformat) {
      case 0:
        return MOODLE;
      case 1:
        return HTML;
      case 2:
        return PLAIN;
      case 4:
        return MARKDOWN;
      default:
        throw new IllegalArgumentException("Unexpected value: " + feedbackformat);
    }
  }

  private final int value;

  private Format(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }
}
