package com.quran.labs.androidquran.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.quran.labs.androidquran.data.ApplicationConstants;


public class QuranSettings {
	
	private static QuranSettings instance = new QuranSettings();
	private boolean arabicNames = false;
	private boolean showClock = false;
	private boolean fullScreen = false;
	private boolean keepScreenOn = false;
	private boolean lockOrientation = false;
	private boolean landscapeOrientation = false;
	private int translationTextSize = ApplicationConstants.DEFAULT_TEXT_SIZE;
	private int lastPage = 0;

	private QuranSettings() {
		
	}

	public Integer getLastPage() {
		return (lastPage == -1)? null : lastPage;
	}

	public void setLastPage(Integer lastPage) {
		this.lastPage = (lastPage == null)? -1 : lastPage;
	}
	
	public boolean isFullScreen() {
		return fullScreen;
	}

	public void setFullScreen(boolean fullScreen) {
		this.fullScreen = fullScreen;
	}

	public static QuranSettings getInstance(){
		return instance;
	}

	public boolean isArabicNames() {
		return arabicNames;
	}

	public void setArabicNames(boolean useArabicNames) {
		this.arabicNames = useArabicNames;
	}

	public boolean isShowClock() {
		return showClock;
	}

	public void setShowClock(boolean showClock) {
		this.showClock = showClock;
	}

	public boolean isKeepScreenOn() {
		return keepScreenOn;
	}

	public void setKeepScreenOn(boolean keepScreenOn) {
		this.keepScreenOn = keepScreenOn;
	}
	
	public boolean isLockOrientation() {
		return lockOrientation;
	}

	public void setLockOrientation(boolean lockOrientation) {
		this.lockOrientation = lockOrientation;
	}
	
	public boolean isLandscapeOrientation() {
		return landscapeOrientation;
	}

	public void setLandscapeOrientation(boolean landscapeOrientation) {
		this.landscapeOrientation = landscapeOrientation;
	}
	
	public int getTranslationTextSize() {
		return translationTextSize;
	}

	public void setTranslationTextSize(int translationTextSize) {
		this.translationTextSize = translationTextSize;
	}

	public static void load(SharedPreferences preferences) {
		instance.arabicNames = preferences.getBoolean(ApplicationConstants.PREF_USE_ARABIC_NAMES, false);
		instance.keepScreenOn = preferences.getBoolean(ApplicationConstants.PREF_KEEP_SCREEN_ON, true);
		instance.fullScreen = preferences.getBoolean(ApplicationConstants.PREF_FULL_SCREEN, false);
		instance.showClock = preferences.getBoolean(ApplicationConstants.PREF_SHOW_CLOCK, false);
		instance.lockOrientation = preferences.getBoolean(ApplicationConstants.PREF_LOCK_ORIENTATION, false);
		instance.landscapeOrientation = preferences.getBoolean(ApplicationConstants.PREF_LANDSCAPE_ORIENTATION, false);
		instance.translationTextSize = preferences.getInt(ApplicationConstants.PREF_TRANSLATION_TEXT_SIZE, ApplicationConstants.DEFAULT_TEXT_SIZE);
		instance.lastPage = preferences.getInt(ApplicationConstants.PREF_LAST_PAGE, -1);
	}
	
	public static void save(SharedPreferences preferences) {
		Editor editor = preferences.edit();
		editor.putBoolean(ApplicationConstants.PREF_USE_ARABIC_NAMES, instance.arabicNames);
		editor.putBoolean(ApplicationConstants.PREF_KEEP_SCREEN_ON, instance.keepScreenOn);
		editor.putBoolean(ApplicationConstants.PREF_FULL_SCREEN, instance.fullScreen);
		editor.putBoolean(ApplicationConstants.PREF_SHOW_CLOCK, instance.showClock);
		editor.putBoolean(ApplicationConstants.PREF_LOCK_ORIENTATION, instance.lockOrientation);
		editor.putBoolean(ApplicationConstants.PREF_LANDSCAPE_ORIENTATION, instance.landscapeOrientation);
		editor.putInt(ApplicationConstants.PREF_TRANSLATION_TEXT_SIZE, instance.translationTextSize);
		editor.putInt(ApplicationConstants.PREF_LAST_PAGE, instance.lastPage);
		editor.commit();
	}
}
