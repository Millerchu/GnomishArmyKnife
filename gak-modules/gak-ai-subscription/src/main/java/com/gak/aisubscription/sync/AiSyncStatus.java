package com.gak.aisubscription.sync;

/** 持久化任务状态；终态只允许显式刷新创建新任务。 */
public enum AiSyncStatus { QUEUED, RUNNING, RETRY, SUCCEEDED, FAILED, CANCELLED }
