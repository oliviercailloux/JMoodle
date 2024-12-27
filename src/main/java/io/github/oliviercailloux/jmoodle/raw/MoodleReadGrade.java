package io.github.oliviercailloux.jmoodle.raw;

record MoodleReadGrade (int userid, String grade) {

  public double gradeAsDouble() {
    return Double.parseDouble(grade);
  }
}
