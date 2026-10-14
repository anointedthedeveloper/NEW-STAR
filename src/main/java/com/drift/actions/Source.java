package com.drift.actions;

/** Where a request came from. Learned sources never get extra authority. */
public enum Source { PARSED, LEARNED_COMMAND, USER_CHOICE, UI }
