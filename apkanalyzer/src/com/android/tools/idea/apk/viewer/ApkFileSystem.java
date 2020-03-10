/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.tools.apk.analyzer.BinaryXmlParser;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Shorts;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.impl.jar.JarHandler;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

public class ApkFileSystem extends ArchiveFileSystem {
  public static final Set<String> EXTENSIONS = ImmutableSet.of(
    SdkConstants.EXT_ANDROID_PACKAGE,
    SdkConstants.EXT_AAR,
    SdkConstants.EXT_INSTANTAPP_PACKAGE,
    SdkConstants.EXT_ZIP,
    SdkConstants.EXT_APP_BUNDLE
  );

  public static final String PROTOCOL = "apk";
  public static final String APK_SEPARATOR = URLUtil.JAR_SEPARATOR;

  private static final String APKZIP_EXT = "apkzip";
  private static final String APKZIP_SUFFIX = "." + APKZIP_EXT;
  private static final Key<Boolean> APKZIP_KEY = Key.create("android.zip.within.apk");

  public static ApkFileSystem getInstance() {
    return (ApkFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  private final DiskQueryRelay<VirtualFile, FileAttributes> myAttrGetter = new DiskQueryRelay<>(super::getAttributes);

  @Nullable
  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    return myAttrGetter.accessDiskWithCheckCanceled(file);
  }

  private final DiskQueryRelay<VirtualFile, String[]> myChildrenGetter = new DiskQueryRelay<>(super::list);

  @Override
  public @NotNull String[] list(@NotNull VirtualFile file) {
    return myChildrenGetter.accessDiskWithCheckCanceled(file);
  }

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @NotNull
  @Override
  protected String extractLocalPath(@NotNull String rootPath) {
    return StringUtil.trimEnd(rootPath, APK_SEPARATOR);
  }

  @NotNull
  @Override
  protected String composeRootPath(@NotNull String localPath) {
    return localPath + APK_SEPARATOR;
  }

  @NotNull
  @Override
  protected ArchiveHandler getHandler(@NotNull VirtualFile entryFile) {
    return VfsImplUtil.getHandler(this, entryFile, JarHandler::new);
  }

  /**
   * Returns the relative path of the file within the APK file system.
   * Same implementation as super, but public access.
   */
  @Override
  @NotNull
  public String getRelativePath(@NotNull VirtualFile file) {
    return super.getRelativePath(file);
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String normalizedPath) {
    int apkSeparatorIndex = normalizedPath.indexOf(APK_SEPARATOR);
    return apkSeparatorIndex > 0 ? normalizedPath.substring(0, apkSeparatorIndex + APK_SEPARATOR.length()) : "";
  }

  @Nullable
  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByPathIfCached(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public void refresh(boolean asynchronous) {
    VfsImplUtil.refresh(this, asynchronous);
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    byte[] bytes = super.contentsToByteArray(file);
    if (!isBinaryXml(file, bytes)) {
      return bytes;
    }

    return BinaryXmlParser.decodeXml(file.getName(), bytes);
  }

  @Override
  protected boolean isCorrectFileType(@NotNull VirtualFile local) {
    if (APKZIP_EXT.equals(local.getExtension()) && Boolean.TRUE.equals(local.getUserData(APKZIP_KEY))) {
      // accept files that were unzipped from within an APK by us, see #extractAndGetContentRoot
      return true;
    }

    return EXTENSIONS.contains(local.getExtension());
  }

  /**
   * Extracts a zip file within an APK file, and returns the virtual file pointing to the contents of that zip file.
   */
  @Nullable
  public VirtualFile extractAndGetContentRoot(VirtualFile file) {
    File tempFile;
    try {
      tempFile = FileUtil.createTempFile(file.getName(), APKZIP_SUFFIX, true);
    }
    catch (IOException e) {
      Logger.getInstance(ApkFileSystem.class).warn("IOException while extracting zip file from APK", e);
      return null;
    }

    try (InputStream is = new ByteArrayInputStream(file.contentsToByteArray())) {
      Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e) {
      Logger.getInstance(ApkFileSystem.class).warn("IOException while copying contents of zip file to temp file", e);
      return null;
    }

    VirtualFile vfile = VfsUtil.findFileByIoFile(tempFile, true);
    if (vfile == null) {
      return null;
    }

    vfile.putUserData(APKZIP_KEY, Boolean.TRUE);
    return getInstance().getRootByLocal(vfile);
  }

  /**
   * @return Whether the given file is a binary XML file within the APK.
   */
  public boolean isBinaryXml(VirtualFile file, byte[] bytes) {
    return isBinaryXml(getRelativePath(file), bytes);
  }

  public static boolean isBinaryXml(String relativePath, byte[] bytes) {
    if (!relativePath.endsWith(SdkConstants.DOT_XML)) {
      return false;
    }

    boolean encodedXmlPath = relativePath.equals(SdkConstants.FN_ANDROID_MANIFEST_XML) ||
                             (relativePath.startsWith(SdkConstants.FD_RES) &&
                              !relativePath.startsWith(SdkConstants.FD_RES + "/" + SdkConstants.FD_RES_RAW));
    if (!encodedXmlPath) {
      return false;
    }

    short code = Shorts.fromBytes(bytes[1], bytes[0]);
    return code == Chunk.Type.XML.code();
  }
}
