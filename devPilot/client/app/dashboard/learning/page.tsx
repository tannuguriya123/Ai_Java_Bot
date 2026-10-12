"use client";

import { RequireAuth } from "@/components/providers/require-auth";
import { AppShell } from "@/components/layout/app-shell";
import { LearningDashboard } from "@/components/learning/learning-dashboard";

export default function LearningPage() {
  return (
    <RequireAuth>
      <AppShell title="My courses">
        <LearningDashboard />
      </AppShell>
    </RequireAuth>
  );
}
