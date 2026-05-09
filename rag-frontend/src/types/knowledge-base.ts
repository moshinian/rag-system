export type KnowledgeBaseStatus = "ACTIVE" | "INACTIVE";

export type KnowledgeBase = {
  id: number;
  kbCode: string;
  name: string;
  description?: string;
  status: KnowledgeBaseStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateKnowledgeBasePayload = {
  kbCode: string;
  name: string;
  description?: string;
  createdBy?: string;
};
