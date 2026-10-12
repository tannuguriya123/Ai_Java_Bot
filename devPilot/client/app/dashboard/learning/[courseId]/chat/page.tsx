"use client";

import { Suspense, use } from "react";

import { LearningChat } from "@/components/learning/learning-chat";
import { RequireAuth } from "@/components/providers/require-auth";
import { Spinner } from "@/components/ui/spinner";

export default function LearningChatRoute({
  params,
}: {
  params: Promise<{ courseId: string }>;
}) {
  const { courseId } = use(params);
  return (
    <RequireAuth>
      <Suspense
        fallback={
          <div className="flex min-h-svh items-center justify-center">
            <Spinner className="size-6" />
          </div>
        }
      >
        <LearningChat courseId={courseId} />
      </Suspense>
    </RequireAuth>
  );
}
