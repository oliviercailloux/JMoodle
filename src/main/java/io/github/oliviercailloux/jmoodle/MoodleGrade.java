package io.github.oliviercailloux.jmoodle;

public record MoodleGrade(int id, int userid, int attemptnumber, int timecreated, int timemodified, int grader, String grade) {
  
}
