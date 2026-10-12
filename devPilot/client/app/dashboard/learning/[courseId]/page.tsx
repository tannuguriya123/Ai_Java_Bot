"use client";

import { use } from "react";

import { RequireAuth } from "@/components/providers/require-auth";
import { LearningCoursePage } from "@/components/learning/learning-course";

export default function LearningCourseRoute({
  params,
}: {
  params: Promise<{ courseId: string }>;
}) {
  const { courseId } = use(params);
  return (
    <RequireAuth>
      <LearningCoursePage courseId={courseId} />
    </RequireAuth>
  );
}
