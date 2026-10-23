import type { Answers } from './api.ts'

// Bài đang làm lưu tạm trong trình duyệt → F5 / đóng tab vẫn không mất. Xoá khi nộp xong.
// localStorage có thể bị chặn (chế độ riêng tư...) nên luôn bọc try/catch.
const key = (examId: string) => `studyforge:exam:${examId}`

export function loadDraft(examId: string): Answers {
  try {
    const raw = localStorage.getItem(key(examId))
    return raw ? (JSON.parse(raw) as Answers) : {}
  } catch {
    return {}
  }
}

export function saveDraft(examId: string, answers: Answers): void {
  try {
    localStorage.setItem(key(examId), JSON.stringify(answers))
  } catch {
    /* không lưu được thì thôi */
  }
}

export function clearDraft(examId: string): void {
  try {
    localStorage.removeItem(key(examId))
  } catch {
    /* bỏ qua */
  }
}
