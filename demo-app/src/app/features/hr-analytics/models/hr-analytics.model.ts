export interface FunnelStageEntry    { stage: string; count: number; }
export interface TimeToHireEntry     { jobTitle: string; avgDays: number; }
export interface TopSkillEntry       { skillName: string; candidateCount: number; }
export interface ApplicationTrendEntry { month: string; count: number; }
export interface ConversionRateEntry { fromStage: string; toStage: string; rate: number; }
