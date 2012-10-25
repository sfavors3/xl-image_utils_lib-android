/*
 * Copyright 2012 Xtreme Labs
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

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import com.xtremelabs.imageutils.AsyncOperationsMaps.AsyncOperationState;

/**
 * This class defensively handles requests from four locations:
 * LifecycleReferenceManager, ImageMemoryCacherInterface,
 * ImageDiskCacherInterface, ImageNetworkInterface and the AsyncOperationsMaps.
 * 
 * The job of this class is to "route" messages appropriately in order to ensure
 * synchronized handling of image downloading and caching operations.
 */
public class ImageCacher implements ImageDownloadObserver, ImageDecodeObserver, AsyncOperationsObserver {
	private static final String PREFIX = "IMAGE CACHER - ";

	private static ImageCacher mImageCacher;

	private ImageDiskCacherInterface mDiskCache;
	private ImageMemoryCacherInterface mMemoryCache;
	private ImageNetworkInterface mNetworkInterface;

	private AsyncOperationsMaps mAsyncOperationsMap;

	private ImageCacher(Context appContext) {
		if (Build.VERSION.SDK_INT <= 11) {
			mMemoryCache = new SizeEstimatingMemoryLRUCacher();
		} else {
			mMemoryCache = new AdvancedMemoryLRUCacher();
		}

		mDiskCache = new DiskLRUCacher(appContext, this);
		mNetworkInterface = new ImageDownloader(mDiskCache, this);
		mAsyncOperationsMap = new AsyncOperationsMaps(this);
	}

	public void clearDiskCache() {
		((DiskLRUCacher) mDiskCache).clearDiskCache();
	}

	public static synchronized ImageCacher getInstance(Context appContext) {
		if (mImageCacher == null) {
			mImageCacher = new ImageCacher(appContext);
		}
		return mImageCacher;
	}

	public ImageReturnValues getBitmapValues(String url, ImageCacherListener imageCacherListener, ScalingInfo scalingInfo, Dimensions dimensions) {
		return getBitmapValues(url, imageCacherListener, scalingInfo);
	}

	public ImageReturnValues getBitmapValues(String url, ImageCacherListener imageCacherListener, ScalingInfo scalingInfo) {
		throwExceptionIfNeeded(url, imageCacherListener, scalingInfo);

		AsyncOperationState asyncOperationState = mAsyncOperationsMap.queueListenerIfRequestPending(imageCacherListener, url, scalingInfo);

		switch (asyncOperationState) {
		case QUEUED_FOR_NETWORK_REQUEST:
			mNetworkInterface.bump(url);
			return null;
		case QUEUED_FOR_DECODE_REQUEST:
			mDiskCache.bumpInQueue(url, getSampleSize(url, scalingInfo));
			return null;
		case NOT_QUEUED:
			return getBitmapFromAppropriateModule(url, imageCacherListener, scalingInfo);
		default:
			return null;
		}
	}

	private ImageReturnValues getBitmapFromAppropriateModule(String url, ImageCacherListener imageCacherListener, ScalingInfo scalingInfo) {
		int sampleSize = getSampleSize(url, scalingInfo);
		// TODO: Look into removing the sampleSize check.

		if (mDiskCache.isCached(url) && sampleSize != -1) {
			Bitmap bitmap;
			if ((bitmap = mMemoryCache.getBitmap(url, sampleSize)) != null) {
				ImageReturnValues imageValues = new ImageReturnValues();
				imageValues.setBitmap(bitmap);
				imageValues.setImageReturnFrom(ImageReturnedFrom.MEMORY);
				imageValues.setDimensions(mDiskCache.getImageDimensions(url));
				return imageValues;
			} else {
				decodeBitmapFromDisk(url, imageCacherListener, sampleSize);
			}
		} else {
			downloadImageFromNetwork(url, imageCacherListener, scalingInfo);
		}
		return null;
	}

	public Bitmap getBitmap(String url, ImageCacherListener imageCacherListener, ScalingInfo scalingInfo) {
		if (Logger.isProfiling()) {
			Profiler.init("getBitmap");
		}

		throwExceptionIfNeeded(url, imageCacherListener, scalingInfo);

		if (Logger.isProfiling()) {
			Profiler.init("Queuing");
		}

		AsyncOperationState asyncOperationState = mAsyncOperationsMap.queueListenerIfRequestPending(imageCacherListener, url, scalingInfo);

		if (Logger.isProfiling()) {
			Profiler.report("Queuing");
		}
		switch (asyncOperationState) {
		case QUEUED_FOR_NETWORK_REQUEST:
			mNetworkInterface.bump(url);

			if (Logger.logAll()) {
				Logger.d(PREFIX + "Queued for network request.");
			}

			if (Logger.isProfiling()) {
				Profiler.report("getBitmap");
			}
			return null;
		case QUEUED_FOR_DECODE_REQUEST:
			mDiskCache.bumpInQueue(url, getSampleSize(url, scalingInfo));

			if (Logger.logAll()) {
				Logger.d(PREFIX + "Queued for decode request.");
			}

			if (Logger.isProfiling()) {
				Profiler.report("getBitmap");
			}
			return null;
		}

		Log.d(ImageLoader.TAG, "Image has not been queued.");

		if (Logger.isProfiling()) {
			Profiler.init("memory or disk");
			Profiler.init("getting sample size");
		}

		int sampleSize = getSampleSize(url, scalingInfo);

		if (Logger.isProfiling()) {
			Profiler.report("getting sample size");
			Profiler.init("checking cached");
		}

		// TODO: Look into removing the sampleSize check.

		if (mDiskCache.isCached(url) && sampleSize != -1) {
			if (Logger.isProfiling()) {
				Profiler.report("checking cached");
			}
			Bitmap bitmap;
			if ((bitmap = mMemoryCache.getBitmap(url, sampleSize)) != null) {

				if (Logger.isProfiling()) {
					Profiler.report("memory or disk");
					Profiler.report("getBitmap");
					Profiler.report("memory or disk");
				}

				if (Logger.logAll()) {
					Logger.d(PREFIX + "Returning bitmap from memory.");
				}

				return bitmap;
			} else {
				if (Logger.logAll()) {
					Logger.d(PREFIX + "Requesting decode from disk.");
				}

				if (Logger.isProfiling()) {
					Profiler.init("decoding from disk");
				}
				decodeBitmapFromDisk(url, imageCacherListener, sampleSize);
				if (Logger.isProfiling()) {
					Profiler.report("decoding from disk");
				}
			}
		} else {
			if (Logger.isProfiling()) {
				Profiler.report("checking cached");
			}
			if (Logger.logAll()) {
				Logger.d(PREFIX + "Downloading image from network.");
			}

			if (Logger.isProfiling()) {
				Profiler.init("downloading from network");
			}
			downloadImageFromNetwork(url, imageCacherListener, scalingInfo);
			if (Logger.isProfiling()) {
				Profiler.report("downloading from network");
			}
		}

		if (Logger.isProfiling()) {
			Profiler.report("memory or disk");
			Profiler.report("getBitmap");
		}

		return null;
	}

	@Override
	public int getSampleSize(String url, ScalingInfo scalingInfo) {
		int sampleSize;
		if (scalingInfo.sampleSize != null) {
			sampleSize = scalingInfo.sampleSize;
		} else {
			sampleSize = mDiskCache.getSampleSize(url, scalingInfo.width, scalingInfo.height);
		}
		return sampleSize;
	}

	/**
	 * Caches the image at the provided url to disk. If the image is already on
	 * disk, it gets bumped on the eviction queue.
	 * 
	 * @param url
	 */
	public synchronized void precacheImage(String url) {
		validateUrl(url);

		if (!mAsyncOperationsMap.isNetworkRequestPendingForUrl(url) && !mDiskCache.isCached(url)) {
			mNetworkInterface.downloadImageToDisk(url);
		} else {
			mDiskCache.bumpOnDisk(url);
		}
	}

	public void clearMemCache() {
		mMemoryCache.clearCache();
	}

	public void setMaximumCacheSize(long size) {
		mMemoryCache.setMaximumCacheSize(size);
	}

	public void cancelRequestForBitmap(ImageCacherListener imageCacherListener) {
		if (Logger.logAll()) {
			Logger.d(PREFIX + "Cancelling a request.");
		}
		mAsyncOperationsMap.cancelPendingRequest(imageCacherListener);
	}

	private void downloadImageFromNetwork(String url, ImageCacherListener imageCacherListener, ScalingInfo scalingInfo) {
		mAsyncOperationsMap.registerListenerForNetworkRequest(imageCacherListener, url, scalingInfo);
		mNetworkInterface.downloadImageToDisk(url);
	}

	private void decodeBitmapFromDisk(String url, ImageCacherListener imageCacherListener, int sampleSize) {
		mAsyncOperationsMap.registerListenerForDecode(imageCacherListener, url, sampleSize);
		mDiskCache.getBitmapAsynchronouslyFromDisk(url, sampleSize, ImageReturnedFrom.DISK, true);
	}

	private void validateUrl(String url) {
		if (url == null || url.length() == 0) {
			throw new IllegalArgumentException("Null URL passed into the image system.");
		}
	}

	private void throwExceptionIfNeeded(String url, ImageCacherListener imageCacherListener, ScalingInfo scalingInfo) {
		ThreadChecker.throwErrorIfOffUiThread();

		validateUrl(url);

		if (imageCacherListener == null) {
			throw new IllegalArgumentException("The ImageCacherListener must not be null.");
		}

		if (scalingInfo == null) {
			throw new IllegalArgumentException("The ScalingInfo must not be null.");
		}
	}

	public static abstract class ImageCacherListener {
		public abstract void onImageAvailable(Bitmap bitmap, ImageReturnedFrom returnedFrom);

		public abstract void onImageAvailable(ImageReturnValues imageValues);

		public abstract void onFailure(String message);
	}

	@Override
	public void onImageDecoded(Bitmap bitmap, String url, int sampleSize, ImageReturnedFrom returnedFrom) {
		mMemoryCache.cacheBitmap(bitmap, url, sampleSize);
		mAsyncOperationsMap.onDecodeSuccess(bitmap, url, sampleSize, returnedFrom);
	}

	@Override
	public void onImageDecoded(ImageReturnValues imageValues, String url, int sampleSize) {
		mMemoryCache.cacheBitmap(imageValues.getBitmap(), url, sampleSize);
		mAsyncOperationsMap.onDecodeSuccess(imageValues, url, sampleSize);

	}

	@Override
	public void onImageDecodeFailed(String url, int sampleSize, String message) {
		mAsyncOperationsMap.onDecodeFailed(url, sampleSize, message);
	}

	@Override
	public void onImageDownloaded(String url) {
		mAsyncOperationsMap.onDownloadComplete(url);
	}

	@Override
	public void onImageDownloadFailed(String url, String message) {
		mAsyncOperationsMap.onDownloadFailed(url, message);
	}

	@Override
	public void onImageDecodeRequired(String url, int sampleSize) {
		mDiskCache.getBitmapAsynchronouslyFromDisk(url, sampleSize, ImageReturnedFrom.NETWORK, false);
	}

	@Override
	public boolean isNetworkRequestPendingForUrl(String url) {
		return mNetworkInterface.isNetworkRequestPendingForUrl(url);
	}

	@Override
	public boolean isDecodeRequestPending(DecodeOperationParameters decodeOperationParameters) {
		return mDiskCache.isDecodeRequestPending(decodeOperationParameters);
	}
}
