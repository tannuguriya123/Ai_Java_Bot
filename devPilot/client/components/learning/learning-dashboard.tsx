"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import {
  ArrowRight,
  BookOpenText,
  ExternalLink,
  FileUp,
  Trash2,
} from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useDeleteLearningCourse,
  useLearningCourses,
  useUploadLearningUnit,
} from "@/hooks/use-learning";

export function LearningDashboard() {
  const coursesQuery = useLearningCourses();
  const upload = useUploadLearningUnit();
  const deleteCourse = useDeleteLearningCourse();
  const [formError, setFormError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<string | null>(null);

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const form = event.currentTarget;
    const formData = new FormData(form);
    const courseName = String(formData.get("courseName") ?? "").trim();
    const files = formData.getAll("files").filter(
      (value): value is File => value instanceof File && value.size > 0
    );
    if (files.length === 0) {
      setFormError("Choose at least one PDF, PPT, or PPTX file to upload.");
      return;
    }
    const invalidFiles = files.filter(
      (file) => !/\.(pdf|pptx?)$/i.test(file.name) || file.size > 25 * 1024 * 1024
    );
    if (invalidFiles.length > 0) {
      setFormError(
        `Check file type and size (PDF, PPT, or PPTX; maximum 25 MB each): ${invalidFiles.map((file) => file.name).join(", ")}`
      );
      return;
    }

    let uploaded = 0;
    const failures: string[] = [];
    for (const [index, file] of files.entries()) {
      setUploadProgress(`Uploading ${index + 1} of ${files.length}: ${file.name}`);
      const unitName = file.name.replace(/\.(pdf|pptx?)$/i, "").trim() || file.name;
      const unitForm = new FormData();
      unitForm.set("courseName", courseName);
      unitForm.set("unitName", unitName);
      unitForm.set("file", file);
      try {
        await upload.mutateAsync(unitForm);
        uploaded++;
      } catch (error) {
        failures.push(
          `${file.name}: ${error instanceof Error ? error.message : "Upload failed"}`
        );
      }
    }

    setUploadProgress(null);
    if (uploaded > 0) {
      form.reset();
      await coursesQuery.refetch();
    }
    if (failures.length > 0) {
      setFormError(
        `${uploaded} of ${files.length} file(s) uploaded. Failed files: ${failures.join("; ")}`
      );
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
      <section className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(20rem,0.8fr)]">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileUp className="size-5" />
              Add course material
            </CardTitle>
            <CardDescription>
              Select the files you downloaded from a GU course. DevPilot creates
              one course card and indexes each file as a separate unit.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={handleUpload}>
              <label className="block space-y-1.5 text-sm font-medium">
                GU course name
                <Input
                  name="courseName"
                  placeholder="e.g. Calculus"
                  required
                  maxLength={200}
                />
              </label>
              <label className="block space-y-1.5 text-sm font-medium">
                Course files (PDF/PPT/PPTX, max 25 MB each)
                <Input
                  name="files"
                  type="file"
                  accept=".pdf,.ppt,.pptx,application/pdf,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"
                  multiple
                  required
                />
              </label>
              {formError && (
                <Alert variant="destructive">
                  <AlertTitle>Upload needs attention</AlertTitle>
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}
              {uploadProgress && (
                <p className="text-sm text-muted-foreground" role="status" aria-live="polite">
                  {uploadProgress}
                </p>
              )}
              <div className="flex flex-wrap items-center gap-3">
                <Button type="submit" disabled={upload.isPending || Boolean(uploadProgress)}>
                  <FileUp data-icon="inline-start" />
                  {upload.isPending || uploadProgress ? "Uploading…" : "Upload and index"}
                </Button>
                <a
                  href="https://gulms.galgotiasuniversity.org/"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
                >
                  Open GU LMS <ExternalLink className="size-3.5" />
                </a>
              </div>
            </form>
          </CardContent>
        </Card>

        <Card className="bg-muted/25">
          <CardHeader>
            <CardTitle>GU LMS helper</CardTitle>
            <CardDescription>
              The optional Chrome/Edge extension detects PDF and PowerPoint
              links visible on the LMS page you are viewing. It does not ask for
              or store your university password.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-muted-foreground">
            <p>
              Install the unpacked extension from the project&apos;s{" "}
              <code>devPilot/extension</code> folder, open GU LMS in a tab, then
              scan and download the materials you want to study. Select the
              downloaded files together using this form; they will be indexed as
              separate units under the GU course name.
            </p>
            <p>
              The helper only reads links on the current LMS page; it does not
              crawl other courses or navigate using your account.
            </p>
            <p>
              For indexing and chat, extracted document text is stored in your
              configured pgvector database and sent to the configured Gemini
              API for embeddings and answers.
            </p>
          </CardContent>
        </Card>
      </section>

      <section className="space-y-4">
        <div>
          <h2 className="font-heading text-lg font-semibold">Your courses</h2>
          <p className="text-sm text-muted-foreground">
            Each uploaded file becomes a searchable unit in its course. Open a
            course card to check indexing and start a course chat.
          </p>
        </div>
        {coursesQuery.isLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-40 rounded-2xl" />
            ))}
          </div>
        ) : coursesQuery.isError ? (
          <Alert variant="destructive">
            <AlertTitle>Could not load courses</AlertTitle>
            <AlertDescription>
              {coursesQuery.error.message}{" "}
              {coursesQuery.error.message.includes("session has expired") && (
                <Link href="/login" className="font-medium underline">
                  Sign in to DevPilot
                </Link>
              )}
            </AlertDescription>
          </Alert>
        ) : coursesQuery.data?.length ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {coursesQuery.data.map((course) => (
              <Card
                key={course.id}
                className="h-full transition-colors hover:ring-foreground/20"
              >
                <Link
                  href={`/dashboard/learning/${course.id}`}
                  className="group block"
                >
                  <CardHeader>
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <span className="rounded-xl bg-muted p-2.5">
                          <BookOpenText className="size-5" />
                        </span>
                        <div className="min-w-0">
                          <CardTitle className="truncate">{course.name}</CardTitle>
                          <CardDescription className="mt-1">
                            {course.unitCount} unit{course.unitCount === 1 ? "" : "s"}
                          </CardDescription>
                        </div>
                      </div>
                      <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
                    </div>
                  </CardHeader>
                  <CardContent className="flex items-center gap-2">
                    <Badge variant="secondary">
                      {course.readyUnitCount} indexed
                    </Badge>
                    <span className="text-xs text-muted-foreground">
                      {course.chunkCount} searchable sections
                    </span>
                  </CardContent>
                </Link>
                <CardContent className="pt-0">
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    disabled={deleteCourse.isPending}
                    onClick={() => {
                      if (
                        window.confirm(
                          `Delete "${course.name}" and all its units, chats, and indexed data? This cannot be undone.`
                        )
                      ) {
                        deleteCourse.mutate(course.id);
                      }
                    }}
                  >
                    <Trash2 data-icon="inline-start" />
                    Delete course
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>No courses yet</CardTitle>
              <CardDescription>
                Upload the first course PDF or PowerPoint to create a course.
              </CardDescription>
            </CardHeader>
          </Card>
        )}
      </section>
    </div>
  );
}
