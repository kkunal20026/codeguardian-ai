export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface Finding {
  id: number;
  source: string;
  type: string;
  project: string;
  module: string | null;
  filePath: string | null;
  title: string;
  details: string;
  severity: string;
  flakinessRate: number;
  priorityScore: number;
  status: string;
  detectedAt: string;
  updatedAt: string;
}

export interface ProposalSummary {
  id: number;
  findingId: number;
  title: string;
  riskLevel: RiskLevel;
  status: string;
  validationStatus: string;
  confidence: number;
  generatedBy: string;
  createdAt: string;
}

export interface ApprovalView {
  approver: string;
  channel: string;
  decision: string;
  comment: string | null;
  decidedAt: string;
}

export interface ExecutionView {
  target: string;
  status: string;
  externalRef: string | null;
  attempts: number;
  lastError: string | null;
  updatedAt: string;
}

export interface LlmCall {
  id: number;
  provider: string;
  model: string;
  systemPrompt: string;
  userPrompt: string;
  rawOutput: string | null;
  promptTokens: number | null;
  completionTokens: number | null;
  latencyMs: number;
  success: boolean;
  errorMessage: string | null;
  createdAt: string;
}

export interface ProposalDetail {
  id: number;
  finding: Finding | null;
  title: string;
  summary: string;
  rootCause: string;
  explanation: string;
  patchDiff: string;
  prDescription: string;
  confidence: number;
  linesChanged: number;
  touchedFiles: string | null;
  validationStatus: string;
  validationLog: string | null;
  riskLevel: RiskLevel;
  riskReasons: string | null;
  status: string;
  generatedBy: string;
  createdAt: string;
  executedAt: string | null;
  approvals: ApprovalView[];
  executions: ExecutionView[];
  llmCalls: LlmCall[];
}

export interface MetricsReport {
  flakyTestRate: number;
  ciNoise: {
    overall: number;
    before: number | null;
    after: number | null;
    reductionPercent: number | null;
    runsBefore: number;
    runsAfter: number;
    splitAt: string | null;
  };
  automatedFixes: number;
  proposalsTotal: number;
  awaitingApproval: number;
  falsePositiveRate: number | null;
  llmFallbacks: number;
  totalRuns: number;
  syntheticRuns: number;
  note: string;
}

export interface AuditEntry {
  seq: number;
  eventType: string;
  entityType: string;
  entityId: string;
  actor: string;
  payloadJson: string;
  eventTime: string;
  previousHash: string;
  contentHash: string;
}

export interface VerificationResult {
  valid: boolean;
  entriesChecked: number;
  firstBrokenSeq: number | null;
  message: string;
}

export interface CycleReport {
  startedAt: string;
  durationMs: number;
  findingsRefreshed: number;
  findingsPlanned: number;
  outcomes: { proposalId: number; findingId: number; title: string; riskLevel: RiskLevel; status: string; generatedBy: string }[];
  errors: string[];
}
