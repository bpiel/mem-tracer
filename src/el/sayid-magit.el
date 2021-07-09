;;; sayid-magit.el --- Choose sayid tracing from magit -*- lexical-binding: t -*-

;; Author: Mark Dawson
;; Maintainer: Bill Piel <bill@billpiel.com>
;; Version: 0.0.1
;; URL: https://github.com/clojure-emacs/sayid
;; Package-Requires: ((cider "0.21.0") (magit "2.90.1"))

;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at

;;     http://www.apache.org/licenses/LICENSE-2.0

;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

;;; Commentary:

;;; Code:

;;;###autoload
(defun sayid-magit--trace-ns-in-files (file-names)
  "Trace namespace in FILE-NAMES."
  (mapc (lambda (file-name)
          (nrepl-send-sync-request (list "op" "sayid-trace-ns-in-file"
                                         "file" file-name)
                                   (cider-current-connection)))
        file-names)
  (sayid-show-traced))

;;;###autoload
(defun sayid-magit--changed-files (git-revision)
  "Return the absolute paths to changed files which have a .clj \
extension in the current .git directory since GIT-REVISION."
  (seq-filter
   (apply-partially #'string-match ".clj$")
   (mapcar
    (lambda (file)
      (expand-file-name file (locate-dominating-file file ".git")))
    (magit-changed-files git-revision))))

;;;###autoload
(defun sayid-magit-trace-changed-ns (git-revision)
  "Trace the changed namespaces in a git commit since GIT-REVISION."
  (interactive (list (magit-read-starting-point "Sayid trace" nil "HEAD")))
  (sayid-magit--trace-ns-in-files
   (sayid-magit--changed-files git-revision)))

(provide 'sayid-magit)

;;; sayid-magit.el ends here
