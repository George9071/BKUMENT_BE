package vn.edu.hcmut.event;

public record EmailSendAfterCommitEvent(String topic, Object payload) {}
