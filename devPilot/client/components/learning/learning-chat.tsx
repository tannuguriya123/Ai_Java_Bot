"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ArrowLeft, Bot, UserRound } from "lucide-react";

import { ChatComposer } from "@/components/chat/chat-composer";
import { ChatMarkdown } from "@/components/chat/chat-markdown";
import { AppShell } from "@/components/layout/app-shell";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useChatMessages, useStreamChat } from "@/hooks/use-chat";
import { useLearningCourse } from "@/hooks/use-learning";
import { api, type ChatMessage, type ChatSession } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

function LearningMessages({
  messages,
  streamText,
  loading,
}: {
  messages: ChatMessage[];
  streamText: string;
  loading: boolean;
}) {
  if (loading) {
    return (
      <div className="flex flex-1 flex-col gap-4 p-6">
        <Skeleton className="h-16 w-2/3 rounded-3xl" />
        <Skeleton className="ml-auto h-12 w-1/2 rounded-3xl" />
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-6">
        {!messages.length && !streamText && (
          <div className="rounded-2xl border border-dashed bg-muted/30 px-6 py-10 text-center">
            <p className="font-medium">Ask about the indexed class material</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Ask for a concept explanation, a worked example, or a summary.
            </p>
          </div>
        )}
        {messages.map((message) => (
          <div
            key={message.id}
            className={`flex items-start gap-3 ${message.role === "USER" ? "flex-row-reverse" : ""}`}
          >
            <Avatar className="size-8 shrink-0">
              <AvatarFallback>
                {message.role === "USER" ? (
                  <UserRound className="size-4" />
                ) : (
                  <Bot className="size-4" />
                )}
              </AvatarFallback>
            </Avatar>
            <div
              className={`max-w-[85%] rounded-2xl px-4 py-3 ${
                message.role === "USER"
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted"
              }`}
            >
              {message.role === "USER" ? (
                <p className="whitespace-pre-wrap text-sm">{message.content}</p>
              ) : (
                <>
                  <ChatMarkdown content={message.content} />
                  {message.citations.length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-1.5 border-t pt-2">
                      {message.citations.map((citation) => (
                        <Badge key={citation.filePath} variant="outline">
                          {citation.filePath}
                        </Badge>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        ))}
        {streamText && (
          <div className="flex items-start gap-3">
            <Avatar className="size-8 shrink-0">
              <AvatarFallback><Bot className="size-4" /></AvatarFallback>
            </Avatar>
            <div className="max-w-[85%] rounded-2xl bg-muted px-4 py-3">
              <ChatMarkdown content={streamText} isStreaming />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function LearningChat({ courseId }: { courseId: string }) {
  const params = useSearchParams();
  const unitId = params.get("unitId") ?? undefined;
  const queryClient = useQueryClient();
  const courseQuery = useLearningCourse(courseId);
  const units = courseQuery.data?.units ?? [];
  const selectedUnit = unitId ? units.find((unit) => unit.id === unitId) : undefined;
  const scopeKey = `${courseId}:${unitId ?? ""}`;
  const [selectedSession, setSelectedSession] = useState<{
    scopeKey: string;
    id: string;
  } | null>(null);
  const autoCreateKeyRef = useRef<string | null>(null);
  const sessionKey = ["chat", "course-sessions", courseId, unitId ?? ""];
  const sessionsQuery = useQuery({
    queryKey: sessionKey,
    queryFn: () => api.listCourseChatSessions(courseId, unitId),
    enabled: Boolean(courseQuery.data) &&
      (unitId ? selectedUnit?.indexStatus === "READY" : units.some((unit) => unit.indexStatus === "READY")),
  });
  const createSession = useMutation({
    mutationFn: () => api.createCourseChatSession(courseId, unitId),
    onSuccess: async (session) => {
      queryClient.setQueryData<ChatSession[]>(sessionKey, (previous) => [
        session,
        ...(previous ?? []),
      ]);
      queryClient.setQueryData(queryKeys.chat.messages(session.id), []);
      setSelectedSession({ scopeKey, id: session.id });
    },
  });
  const sessionId =
    (selectedSession?.scopeKey === scopeKey ? selectedSession.id : null) ??
    sessionsQuery.data?.[0]?.id ??
    null;
  const messagesQuery = useChatMessages(sessionId);
  const { send, stop, streaming, streamText } = useStreamChat(sessionId);

  useEffect(() => {
    if (
      !sessionsQuery.isSuccess ||
      (sessionsQuery.data?.length ?? 0) > 0 ||
      createSession.isPending ||
      autoCreateKeyRef.current === scopeKey
    ) {
      return;
    }
    autoCreateKeyRef.current = scopeKey;
    createSession.mutate();
  }, [courseId, unitId, scopeKey, sessionsQuery.isSuccess, sessionsQuery.data, createSession]);

  if (courseQuery.isLoading) {
    return (
      <AppShell title="Loading course">
        <Skeleton className="m-4 min-h-80 rounded-2xl" />
      </AppShell>
    );
  }

  if (courseQuery.isError || !courseQuery.data) {
    return (
      <AppShell title="Course unavailable">
        <p className="p-6 text-sm text-destructive">
          {courseQuery.error?.message ?? "Course not found"}
        </p>
      </AppShell>
    );
  }

  const ready = unitId
    ? selectedUnit?.indexStatus === "READY"
    : units.some((unit) => unit.indexStatus === "READY");

  return (
    <AppShell
      title={courseQuery.data.name}
      description={`Learning chat${selectedUnit ? ` · ${selectedUnit.name}` : ""}`}
      actions={
        <Button
          nativeButton={false}
          variant="outline"
          size="sm"
          render={<Link href={`/dashboard/learning/${courseId}`} />}
        >
          <ArrowLeft data-icon="inline-start" />
          Units
        </Button>
      }
    >
      <div className="flex min-h-[70vh] flex-1 flex-col">
        {!ready ? (
          <div className="m-auto max-w-lg p-8 text-center">
            <h2 className="font-medium">
              {unitId && !selectedUnit ? "Unit not found" : "Material is not ready yet"}
            </h2>
            <p className="mt-2 text-sm text-muted-foreground">
              {unitId && !selectedUnit
                ? "Return to the course and choose one of its units."
                : "Index a course unit before starting a chat."}
            </p>
          </div>
        ) : (
          <>
            {(sessionsQuery.isError || createSession.isError) && (
              <div className="px-4 pt-4">
                <Alert variant="destructive">
                  <AlertTitle>Could not start the learning chat</AlertTitle>
                  <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
                    {sessionsQuery.error?.message ??
                      createSession.error?.message ??
                      "Please try again."}
                    {sessionsQuery.isError ? (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => void sessionsQuery.refetch()}
                      >
                        Retry
                      </Button>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={createSession.isPending}
                        onClick={() => createSession.mutate()}
                      >
                        Retry
                      </Button>
                    )}
                  </AlertDescription>
                </Alert>
              </div>
            )}
            <LearningMessages
              messages={messagesQuery.data ?? []}
              streamText={streamText}
              loading={messagesQuery.isLoading}
            />
            <ChatComposer
              disabled={!sessionId}
              streaming={streaming}
              onSend={send}
              onStop={stop}
            />
          </>
        )}
      </div>
    </AppShell>
  );
}
