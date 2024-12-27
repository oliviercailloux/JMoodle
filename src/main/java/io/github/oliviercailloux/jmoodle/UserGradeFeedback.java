package io.github.oliviercailloux.jmoodle;

import io.github.oliviercailloux.jmoodle.raw.Feedback;

public record UserGradeFeedback(UserId userId, String username, int assignmentId, String assignmentName, double grade, Feedback feedback) {

}
