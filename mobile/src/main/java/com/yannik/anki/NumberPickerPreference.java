package com.yannik.anki;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

public class NumberPickerPreference extends DialogPreference {

    private static final String TAG = NumberPickerPreference.class.getSimpleName();

    private final int DEFAULT_VALUE = 0;

    Integer mValue;
    NumberPicker mNumberPicker;
    String summaryPostFix = "";
    private int min = 0, max = 100;

    /*
     * We declare the layout resource file as well as the
     * text for the positive and negative dialog buttons.
     *
     * If required, instead of using `setDialogLayoutResource()`
     * to specify the layout, you can override `onCreateDialogView()`
     * and generate the View to display in the dialog right there.
     * */
    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.numberpicker_dialog);
        setPositiveButtonText("OK");
        setNegativeButtonText("Cancel");


        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.NumberPickerPreference);
        final int N = a.getIndexCount();
        for (int i = 0; i < N; ++i) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.NumberPickerPreference_minValue:
                    min = a.getInt(attr, 0);
                    break;
                case R.styleable.NumberPickerPreference_maxValue:
                    max = a.getInt(attr, 100);
                    break;
                case R.styleable.NumberPickerPreference_summaryPostFix:
                    summaryPostFix = a.getString(attr);
                    break;
            }
        }
        a.recycle();


    }

    /*
     * Bind data to our content views
     * */
    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        // Set min and max values to our NumberPicker
        mNumberPicker = view.findViewById(R.id.numberPicker);
        mNumberPicker.setMinValue(min);
        mNumberPicker.setMaxValue(max);

        // Set default/current/selected value if set
        if (mValue != null) mNumberPicker.setValue(mValue);
    }

    /*
     * Called when the dialog is closed.
     * If the positive button was clicked then persist
     * the data (save in SharedPreferences by calling `persistInt()`)
     * */
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            mValue = mNumberPicker.getValue();
            persistInt(mValue);
            setSummary(mValue + summaryPostFix);
            if (getOnPreferenceChangeListener() != null)
                getOnPreferenceChangeListener().onPreferenceChange(this, mValue);
        }
    }

    /*
     * Set initial value of the preference. Called when
     * the preference object is added to the screen.
     *
     * If `restorePersistedValue` is true, the Preference
     * value should be restored from the SharedPreferences
     * else the Preference value should be set to defaultValue
     * passed and it should also be persisted (saved).
     *
     * `restorePersistedValue` will generally be false when
     * you've specified `android:defaultValue` that calls
     * `onGetDefaultValue()` (check below) and that in turn
     * returns a value which is passed as the `defaultValue`
     * to `onSetInitialValue()`.
     * */
    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        // Log.d(TAG, "boolean: " + restorePersistedValue + " object: " + defaultValue);
        if (restorePersistedValue) {
            mValue = getPersistedInt(DEFAULT_VALUE);
        } else {
            mValue = (int) defaultValue;
            persistInt(mValue);
        }
        setSummary(mValue + summaryPostFix);
    }

    /*
     * Called when you set `android:defaultValue`
     *
     * Just incase the value is undefined, you can return
     * DEFAULT_VALUE so that it gets passed to `onSetInitialValue()`
     * that gets saved in SharedPreferences.
     * */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // Log.d(TAG, "Index: " + index + " Value: " + a.getInteger(index, DEFAULT_VALUE));
        //return super.onGetDefaultValue(a, index);
        return a.getInteger(index, DEFAULT_VALUE);
    }
}