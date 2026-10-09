package com.famiglia.tripcompanion.nanocheck;

interface AiEngine {
    interface Progress { void update(String message); }
    int checkStatus() throws Exception;
    int download(Progress progress) throws Exception;
    String generate(String prompt) throws Exception;
}
