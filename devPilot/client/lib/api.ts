export type IndexStatus = "PENDING" | "INDEXING" | "READY" | "FAILED";

export type User = {
  id: string;
  githubId: number;
  githubUsername: string;
  displayName: string;
  avatarUrl: string | null;
};

export type Repository = {
  id: string;
  githubRepoId: number;
  owner: string;
  name: string;
  fullName: string;
  isPrivate: boolean;
  defaultBranch: string;
  language: string | null;
  htmlUrl: string | null;
  description: string | null;
  indexStatus: IndexStatus;
  indexedAt: string | null;
  chunkCount: number;
  filesTotal: number;
  filesProcessed: number;
  errorMessage: string | null;
};

export type IndexStatusResponse = {
  repositoryId: string;
  indexStatus: IndexStatus;
  filesTotal: number;
  filesProcessed: number;
  chunkCount: number;
  indexedAt: string | null;
  errorMessage: string | null;
};

export type ChatSession = {
  id: string;
  repositoryId: string | null;
  courseId: string | null;
  unitId: string | null;
  title: string;
  createdAt: string;
};

export type LearningCourse = {
  id: string;
  name: string;
  unitCount: number;
  readyUnitCount: number;
  chunkCount: number;
  updatedAt: string;
};

export type LearningUnit = {
  id: string;
  courseId: string;
  name: string;
  fileName: string;
  mediaType: string | null;
  indexStatus: IndexStatus;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
};

export type LearningCourseDetail = {
  id: string;
  name: string;
  units: LearningUnit[];
};

export type Citation = {
  filePath: string;
  startLine: number | null;
  endLine: number | null;
  language: string | null;
};

export type ChatMessage = {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  citations: Citation[];
  createdAt: string;
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
}

export function getGitHubLoginUrl() {
  return `${getApiBaseUrl()}/oauth2/authorization/github`;
}

async function parseError(res: Response): Promise<string> {
  try {
    const data = await res.json();
    const message = data.message ?? data.error;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  } catch {
    // Use the HTTP status below when the server sends no JSON error body.
  }

  if (res.status === 401) {
    return "Your DevPilot session has expired. Sign in again, then reopen My courses.";
  }
  if (res.status === 403) {
    return "You do not have permission to perform this action.";
  }
  return res.statusText
    ? `Request failed (${res.status} ${res.statusText})`
    : `Request failed (HTTP ${res.status})`;
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!(init?.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${getApiBaseUrl()}${path}`, {
    ...init,
    credentials: "include",
    headers,
  });

  if (!res.ok) {
    throw new ApiError(res.status, await parseError(res));
  }

  if (res.status === 204) {
    return undefined as T;
  }

  return res.json() as Promise<T>;
}

export const api = {
  me: () => apiFetch<User>("/api/auth/me"),
  logout: () =>
    apiFetch<void>("/api/auth/logout", {
      method: "POST",
    }),
  listRepos: (refresh = true) =>
    apiFetch<Repository[]>(`/api/repos?refresh=${refresh}`),
  getRepo: (id: string) => apiFetch<Repository>(`/api/repos/${id}`),
  startIndex: (id: string) =>
    apiFetch<Repository>(`/api/repos/${id}/index`, { method: "POST" }),
  indexStatus: (id: string) =>
    apiFetch<IndexStatusResponse>(`/api/repos/${id}/status`),
  createSession: (repositoryId: string, title?: string) =>
    apiFetch<ChatSession>("/api/chat/sessions", {
      method: "POST",
      body: JSON.stringify({ repositoryId, title }),
    }),
  listSessions: (repositoryId: string) =>
    apiFetch<ChatSession[]>(
      `/api/chat/sessions?repositoryId=${encodeURIComponent(repositoryId)}`
    ),
  getMessages: (sessionId: string) =>
    apiFetch<ChatMessage[]>(`/api/chat/sessions/${sessionId}`),
  listLearningCourses: () =>
    apiFetch<LearningCourse[]>("/api/learning/courses"),
  getLearningCourse: (courseId: string) =>
    apiFetch<LearningCourseDetail>(`/api/learning/courses/${courseId}`),
  deleteLearningCourse: (courseId: string) =>
    apiFetch<void>(`/api/learning/courses/${courseId}`, { method: "DELETE" }),
  deleteLearningUnit: (courseId: string, unitId: string) =>
    apiFetch<void>(`/api/learning/courses/${courseId}/units/${unitId}`, {
      method: "DELETE",
    }),
  uploadLearningUnit: (formData: FormData) =>
    apiFetch<LearningUnit>("/api/learning/units", {
      method: "POST",
      body: formData,
    }),
  createCourseChatSession: (
    courseId: string,
    unitId?: string,
    title?: string
  ) =>
    apiFetch<ChatSession>("/api/chat/course-sessions", {
      method: "POST",
      body: JSON.stringify({ courseId, unitId, title }),
    }),
  listCourseChatSessions: (courseId: string, unitId?: string) => {
    const params = new URLSearchParams({ courseId });
    if (unitId) params.set("unitId", unitId);
    return apiFetch<ChatSession[]>(`/api/chat/course-sessions?${params}`);
  },
};
