"use client";

import Link from "next/link";
import {
  ArrowLeft,
  FileText,
  MessageSquareText,
  RotateCw,
  Trash2,
} from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useDeleteLearningUnit, useLearningCourse } from "@/hooks/use-learning";

export function LearningCoursePage({ courseId }: { courseId: string }) {
  const courseQuery = useLearningCourse(courseId);
  const deleteUnit = useDeleteLearningUnit();

  if (courseQuery.isLoading) {
    return (
      <AppShell title="Loading course">
        <div className="grid gap-4 p-4 sm:grid-cols-2">
          <Skeleton className="h-40 rounded-2xl" />
          <Skeleton className="h-40 rounded-2xl" />
        </div>
      </AppShell>
    );
  }

  if (courseQuery.isError || !courseQuery.data) {
    return (
      <AppShell title="Course unavailable">
        <div className="p-4">
          <Alert variant="destructive">
            <AlertTitle>Could not load this course</AlertTitle>
            <AlertDescription>
              {courseQuery.error?.message ?? "Course not found"}
            </AlertDescription>
          </Alert>
        </div>
      </AppShell>
    );
  }

  const course = courseQuery.data;
  const hasReadyUnits = course.units.some((unit) => unit.indexStatus === "READY");

  return (
    <AppShell
      title={course.name}
      description={`${course.units.length} learning unit${course.units.length === 1 ? "" : "s"}`}
      actions={
        <>
          {hasReadyUnits && (
            <Button
              nativeButton={false}
              size="sm"
              render={<Link href={`/dashboard/learning/${course.id}/chat`} />}
            >
              <MessageSquareText data-icon="inline-start" />
              Ask this course
            </Button>
          )}
          <Button
            nativeButton={false}
            variant="outline"
            size="sm"
            render={<Link href="/dashboard/learning" />}
          >
            <ArrowLeft data-icon="inline-start" />
            Courses
          </Button>
        </>
      }
    >
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        {course.units.length ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {course.units.map((unit, index) => {
              const ready = unit.indexStatus === "READY";
              return (
                <Card key={unit.id}>
                  <CardHeader>
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-start gap-3">
                        <span className="rounded-xl bg-muted p-2.5">
                          <FileText className="size-5" />
                        </span>
                        <div className="min-w-0">
                          <CardDescription>Unit {index + 1}</CardDescription>
                          <CardTitle className="mt-1 line-clamp-2">
                            {unit.name}
                          </CardTitle>
                        </div>
                      </div>
                      <Badge
                        variant={
                          unit.indexStatus === "FAILED"
                            ? "destructive"
                            : ready
                              ? "secondary"
                              : "outline"
                        }
                      >
                        {unit.indexStatus.toLowerCase()}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="mt-auto space-y-3">
                    <p className="truncate text-xs text-muted-foreground" title={unit.fileName}>
                      {unit.fileName}
                    </p>
                    {unit.errorMessage && (
                      <p className="text-sm text-destructive">{unit.errorMessage}</p>
                    )}
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs text-muted-foreground">
                        {unit.chunkCount} indexed sections
                      </span>
                      {ready ? (
                        <div className="flex items-center gap-1">
                          <Button
                            nativeButton={false}
                            size="sm"
                            variant="secondary"
                            render={
                              <Link
                                href={`/dashboard/learning/${course.id}/chat?unitId=${unit.id}`}
                              />
                            }
                          >
                            <MessageSquareText data-icon="inline-start" />
                            Ask unit
                          </Button>
                        </div>
                      ) : (
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled
                          aria-label="Indexing"
                        >
                          <RotateCw data-icon="inline-start" />
                          {unit.indexStatus === "FAILED" ? "Failed" : "Indexing"}
                        </Button>
                      )}
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        className="text-destructive hover:text-destructive"
                        aria-label={`Delete ${unit.name}`}
                        title="Delete unit"
                        disabled={deleteUnit.isPending}
                        onClick={() => {
                          if (
                            window.confirm(
                              `Delete "${unit.name}" and its indexed data and chats? This cannot be undone.`
                            )
                          ) {
                            deleteUnit.mutate({ courseId: course.id, unitId: unit.id });
                          }
                        }}
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>No units in this course</CardTitle>
              <CardDescription>
                Upload a PDF or PowerPoint from My courses to add the first unit.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button
                nativeButton={false}
                render={<Link href="/dashboard/learning" />}
              >
                Back to courses
              </Button>
            </CardContent>
          </Card>
        )}
      </div>
    </AppShell>
  );
}
