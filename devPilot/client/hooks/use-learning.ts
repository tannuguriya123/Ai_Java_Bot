"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { toast } from "@/components/ui/toast";

export function useLearningCourses() {
  return useQuery({
    queryKey: queryKeys.learning.courses(),
    queryFn: api.listLearningCourses,
    staleTime: 30_000,
  });
}

export function useLearningCourse(courseId: string) {
  return useQuery({
    queryKey: queryKeys.learning.course(courseId),
    queryFn: () => api.getLearningCourse(courseId),
    enabled: Boolean(courseId),
    refetchInterval: (query) =>
      query.state.data?.units.some(
        (unit) => unit.indexStatus === "PENDING" || unit.indexStatus === "INDEXING"
      )
        ? 2000
        : false,
  });
}

export function useUploadLearningUnit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (formData: FormData) => api.uploadLearningUnit(formData),
    onSuccess: async (unit) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.learning.courses() }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.learning.course(unit.courseId),
        }),
      ]);
      toast.add({
        title: "Material uploaded",
        description: "Indexing started. This unit will be ready to chat shortly.",
        type: "success",
      });
    },
    onError: (error: Error) => {
      toast.add({
        title: "Could not upload material",
        description: error.message,
        type: "error",
      });
    },
  });
}

export function useDeleteLearningCourse() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (courseId: string) => api.deleteLearningCourse(courseId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.learning.courses(),
      });
      toast.add({
        title: "Course deleted",
        description: "Course units, chats, and indexed vectors were removed.",
        type: "success",
      });
    },
    onError: (error: Error) => {
      toast.add({
        title: "Could not delete course",
        description: error.message,
        type: "error",
      });
    },
  });
}

export function useDeleteLearningUnit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ courseId, unitId }: { courseId: string; unitId: string }) =>
      api.deleteLearningUnit(courseId, unitId),
    onSuccess: async (_, { courseId }) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: queryKeys.learning.courses(),
        }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.learning.course(courseId),
        }),
      ]);
      toast.add({
        title: "Course file deleted",
        description: "The unit, its chats, and indexed vectors were removed.",
        type: "success",
      });
    },
    onError: (error: Error) => {
      toast.add({
        title: "Could not delete course file",
        description: error.message,
        type: "error",
      });
    },
  });
}
