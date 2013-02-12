/*
 * Copyright 2013 Xtreme Labs
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xtremelabs.imageutils;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;

import android.graphics.Bitmap;

import com.xtremelabs.imageutils.DiskLRUCacher.FileFormatException;

/**
 * This interface defines the mechanisms that the ImageCacher uses to interract with the Disk Cache.
 */
interface ImageDiskCacherInterface extends NetworkToDiskInterface {
	boolean isCached(String uri);

	int getSampleSize(ImageRequest imageRequest);

	void bumpOnDisk(String uri);

	void setDiskCacheSize(long sizeInBytes);

	Dimensions getImageDimensions(String uri);

	void invalidateFileSystemUri(String uri);

	Bitmap getBitmapSynchronouslyFromDisk(DecodeSignature decodeSignature) throws FileNotFoundException, FileFormatException;

	void calculateAndSaveImageDetails(String uri) throws URISyntaxException, FileNotFoundException;

	Prioritizable getDetailsPrioritizable(ImageRequest imageRequest);

	Prioritizable getDecodePrioritizable(DecodeSignature decodeSignature, ImageReturnedFrom imageReturnedFrom);
}