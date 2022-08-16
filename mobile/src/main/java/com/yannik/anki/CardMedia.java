package com.yannik.anki;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import com.google.android.gms.wearable.Asset;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yannik on 09.05.2015.
 */
public class CardMedia {
    /**
     * Group 1 = Contents of [sound:] tag <br>
     * Group 2 = "fname"
     */
    private static final Pattern fSoundRegexps = Pattern.compile("(?i)(\\[sound:([^]]+)])");

    // src element quoted case
    /**
     * Group 1 = Contents of <img> tag <br>
     * Group 2 = "str" <br>
     * Group 3 = "fname" <br>
     * Group 4 = Backreference to "str" (i.e., same type of quote character)
     */
    private static final Pattern fImgRegExpQ = Pattern.compile("(?i)(<img[^>]* src=([\"'])([^>]+?)(\\2)[^>]*>)");
    public static String mediaFolder = null;

    /**
     * Percent-escape UTF-8 characters in local image filenames.
     *
     * @param string The string to search for image references and escape the filenames.
     * @return The string with the filenames of any local images percent-escaped as UTF-8.
     */
    public static String escapeImages(String string, boolean unescape) {
        Matcher m = fImgRegExpQ.matcher(string);

        int fnameIdx = 3;
        while (m.find()) {
            String tag = m.group(0);
            String fname = m.group(fnameIdx);
            if (unescape) {
                string = string.replace(tag, tag.replace(fname, Uri.decode(fname)));
            } else {
                string = string.replace(tag, tag.replace(fname, Uri.encode(fname)));
            }
        }
        return string;
    }

    public static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    // since this is for a static Util Class pass int he resources as well
    public static Bitmap pullScaledBitmap(String path, int maxWidth, int maxHeight, boolean keepAspectRatio) {

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        // set the option to just decode the bounds because we just want
        // the dimensions, not the whole image
        bitmapOptions.inJustDecodeBounds = true;
        // now decode our resource using these options
        BitmapFactory.decodeFile(path, bitmapOptions);
        // since we have pulled the dimensions we can set this back to false
        // because the next time we decode we want the whole image
        bitmapOptions.inJustDecodeBounds = false;

        int width = maxWidth, height = maxHeight;
        if (keepAspectRatio) {
            width = bitmapOptions.outWidth;
            height = bitmapOptions.outHeight;
            if (width > height) {
                // landscape
                float ratio = (float) width / maxWidth;
                width = maxWidth;
                height = (int) (height / ratio);
            } else if (height > width) {
                // portrait
                float ratio = (float) height / maxHeight;
                height = maxHeight;
                width = (int) (width / ratio);
            } else {
                // square
                height = maxHeight;
                width = maxWidth;
            }
        }
        // look below to see the method to use to update the options to set
        // the sample size so we decode at the right size
        bitmapOptions.inSampleSize = calculateInSampleSize(bitmapOptions, width, height);
        // now decode the resource again and return it, because it decodes
        // as the scaled image!
        return BitmapFactory.decodeFile(path, bitmapOptions);
    }

    public static int calculateInSampleSize(BitmapFactory.Options bitmapOptions, int reqWidth, int reqHeight) {
        final int height = bitmapOptions.outHeight;
        final int width = bitmapOptions.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static String getMediaPath(String name) {
        if (mediaFolder == null || !new File(mediaFolder).exists()) {
            mediaFolder = Environment.getExternalStorageDirectory().getAbsolutePath() + "/AnkiDroid/collection.media/";
        }
        return new File(mediaFolder, name).getAbsolutePath();
    }
}
