// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.lfs.fs;

import static org.eclipse.jgit.lfs.lib.Constants.DOWNLOAD;
import static org.eclipse.jgit.lfs.lib.Constants.UPLOAD;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.server.fs.FileLfsServlet;
import org.eclipse.jgit.lfs.server.fs.ObjectDownloadListener;
import org.eclipse.jgit.lfs.server.fs.ObjectUploadListener;
import org.eclipse.jgit.lfs.server.internal.LfsServerText;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LfsFsContentServlet extends FileLfsServlet {
  public interface Factory {
    LfsFsContentServlet create(LocalLargeFileRepository largeFileRepository);
  }

  private static final long serialVersionUID = 1L;

  private final LfsFsRequestAuthorizer authorizer;
  private final LocalLargeFileRepository repository;
  private final long timeout;

  @Inject
  public LfsFsContentServlet(LfsFsRequestAuthorizer authorizer,
      @Assisted LocalLargeFileRepository repository) {
    super(repository, 0);
    this.authorizer = authorizer;
    this.repository = repository;
    this.timeout = 0;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws ServletException, IOException {
    AnyLongObjectId obj = getObjectToTransfer(req, rsp);
    if (obj == null) {
      return;
    }

    if (repository.getSize(obj) == -1) {
      sendError(rsp, HttpStatus.SC_NOT_FOUND, MessageFormat
          .format(LfsServerText.get().objectNotFound, obj.getName()));
      return;
    }

    if (!authorizer.verifyAuthInfo(req.getHeader(HDR_AUTHORIZATION),
        DOWNLOAD, obj)) {
      sendError(rsp, HttpStatus.SC_UNAUTHORIZED, MessageFormat.format(
          LfsServerText.get().failedToCalcSignature, "Invalid authorization token"));
      return;
    }

    AsyncContext context = req.startAsync();
    context.setTimeout(timeout);
    rsp.getOutputStream().setWriteListener(
        new ObjectDownloadListener(repository, context, rsp, obj));
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse rsp)
      throws ServletException, IOException {
    AnyLongObjectId id = getObjectToTransfer(req, rsp);
    if (id == null) {
      return;
    }

    if (!authorizer.verifyAuthInfo(
        req.getHeader(HDR_AUTHORIZATION), UPLOAD, id)) {
      sendError(rsp, HttpStatus.SC_UNAUTHORIZED,
          MessageFormat.format(LfsServerText.get().failedToCalcSignature,
              "Invalid authorization token"));
      return;
    }

    AsyncContext context = req.startAsync();
    context.setTimeout(timeout);
    req.getInputStream().setReadListener(
        new ObjectUploadListener(repository, context, req, rsp, id));
  }
}