/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.course.config.ui;

import java.util.Optional;

import org.olat.commons.calendar.CalendarManager;
import org.olat.commons.calendar.CalendarModule;
import org.olat.commons.calendar.ui.events.CalendarGUIModifiedEvent;
import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.form.flexible.FormItem;
import org.olat.core.gui.components.form.flexible.FormItemContainer;
import org.olat.core.gui.components.form.flexible.elements.FormLink;
import org.olat.core.gui.components.form.flexible.elements.SelectionElement;
import org.olat.core.gui.components.form.flexible.elements.StaticTextElement;
import org.olat.core.gui.components.form.flexible.impl.FormBasicController;
import org.olat.core.gui.components.form.flexible.impl.FormEvent;
import org.olat.core.gui.components.form.flexible.impl.FormLayoutContainer;
import org.olat.core.gui.components.form.flexible.impl.elements.FormSubmit;
import org.olat.core.gui.components.link.Link;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.generic.closablewrapper.CloseableModalController;
import org.olat.core.id.OLATResourceable;
import org.olat.core.logging.activity.ILoggingAction;
import org.olat.core.logging.activity.LearningResourceLoggingAction;
import org.olat.core.logging.activity.ThreadLocalUserActivityLogger;
import org.olat.core.util.StringHelper;
import org.olat.core.util.Util;
import org.olat.core.util.coordinate.CoordinatorManager;
import org.olat.core.util.coordinate.LockResult;
import org.olat.core.util.resource.OresHelper;
import org.olat.course.CourseFactory;
import org.olat.course.ICourse;
import org.olat.course.config.CourseConfig;
import org.olat.course.config.CourseConfigEvent;
import org.olat.course.config.CourseConfigEvent.CourseConfigType;
import org.olat.fileresource.types.BlogFileResource;
import org.olat.fileresource.types.WikiResource;
import org.olat.repository.RepositoryEntry;
import org.olat.repository.RepositoryEntryManagedFlag;
import org.olat.repository.RepositoryManager;
import org.olat.repository.RepositoryService;
import org.olat.repository.controllers.ReferencableEntriesSearchController;
import org.olat.repository.ui.settings.ReloadSettingsEvent;
import org.olat.resource.references.Reference;
import org.olat.resource.references.ReferenceManager;
import org.olat.user.UserManager;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 29 Oct 2018<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
public class CourseToolbarController extends FormBasicController {
	
	private static final String[] onKeys = new String[] {"xx"};
	private final String[] onValues;
	
	private SelectionElement toolbarEl;
	private StaticTextElement explainEl;
	private SelectionElement searchEl;
	private SelectionElement calendarEl;
	private SelectionElement participantListEl;
	private SelectionElement participantInfoEl;
	private SelectionElement emailEl;
	private SelectionElement blogEl;
	private FormLayoutContainer blogCont;
	private FormLink blogOpenLink;
	private FormLink blogSelectLink;
	private SelectionElement wikiEl;
	private FormLayoutContainer wikiCont;
	private FormLink wikiOpenLink;
	private FormLink wikiSelectLink;
	private SelectionElement forumEl;
	private SelectionElement documentsEl;
	private SelectionElement chatEl;
	private SelectionElement glossaryEl;

	private CloseableModalController cmc;
	private ReferencableEntriesSearchController blogSearchCtrl;
	private ReferencableEntriesSearchController wikiSearchCtrl;
	
	private LockResult lockEntry;
	private final boolean editable;
	private RepositoryEntry entry;
	private CourseConfig courseConfig;
	private RepositoryEntry blogEntry;
	private RepositoryEntry wikiEntry;

	@Autowired
	private UserManager userManager;
	@Autowired
	private CalendarModule calendarModule;
	@Autowired
	private RepositoryManager repositoryManager;
	@Autowired
	private ReferenceManager referenceManager;
	
	public CourseToolbarController(UserRequest ureq, WindowControl wControl,
			RepositoryEntry entry, CourseConfig courseConfig) {
		super(ureq, wControl, Util.createPackageTranslator(RepositoryService.class, ureq.getLocale()));
		onValues = new String[] {translate("on")};
		this.entry = entry;
		this.courseConfig = courseConfig;
		if (StringHelper.containsNonWhitespace(courseConfig.getBlogSoftKey())) {
			blogEntry = repositoryManager.lookupRepositoryEntryBySoftkey(courseConfig.getBlogSoftKey(), false);
		}
		if (StringHelper.containsNonWhitespace(courseConfig.getWikiSoftKey())) {
			wikiEntry = repositoryManager.lookupRepositoryEntryBySoftkey(courseConfig.getWikiSoftKey(), false);
		}
		
		lockEntry = CoordinatorManager.getInstance().getCoordinator().getLocker()
				.acquireLock(entry.getOlatResource(), getIdentity(), CourseFactory.COURSE_EDITOR_LOCK, getWindow());
		editable = (lockEntry != null && lockEntry.isSuccess());
		
		initForm(ureq);
		
		if(lockEntry != null && !lockEntry.isSuccess()) {
			String lockerName = "???";
			if(lockEntry.getOwner() != null) {
				lockerName = userManager.getUserDisplayName(lockEntry.getOwner());
			}
			if(lockEntry.isDifferentWindows()) {
				showWarning("error.editoralreadylocked.same.user", new String[] { lockerName });
			} else {
				showWarning("error.editoralreadylocked", new String[] { lockerName });
			}
		}
	}

	@Override
	protected void doDispose() {
		if (lockEntry != null && lockEntry.isSuccess()) {
			CoordinatorManager.getInstance().getCoordinator().getLocker().releaseLock(lockEntry);
			lockEntry = null;
		}
	}

	@Override
	protected void initForm(FormItemContainer formLayout, Controller listener, UserRequest ureq) {
		setFormContextHelp("Course Settings#_optionen");
		setFormTitle("details.toolbar.title");
		formLayout.setElementCssClass("o_sel_toolbar_settings");
		
		toolbarEl = uifactory.addCheckboxesHorizontal("toolbarIsOn", "chkbx.toolbar.onoff", formLayout, onKeys, onValues);
		toolbarEl.select(onKeys[0], courseConfig.isToolbarEnabled());
		toolbarEl.addActionListener(FormEvent.ONCHANGE);
		toolbarEl.setEnabled(editable);
		
		explainEl = uifactory.addStaticTextElement("chkbx.toolbar.explain", "", formLayout);

		boolean canHideToolbar = true;

		boolean searchEnabled = courseConfig.isCourseSearchEnabled();
		boolean managedSearch = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.search);
		searchEl = uifactory.addCheckboxesHorizontal("searchIsOn", "chkbx.search.onoff", formLayout, onKeys, onValues);
		searchEl.select(onKeys[0], searchEnabled);
		searchEl.setEnabled(editable && !managedSearch);
		if(managedSearch && searchEnabled) {
			canHideToolbar &= false;
		}
		
		if(calendarModule.isEnabled() && calendarModule.isEnableCourseToolCalendar()) {
			boolean calendarEnabled = courseConfig.isCalendarEnabled();
			boolean managedCal = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.calendar);
			calendarEl = uifactory.addCheckboxesHorizontal("calIsOn", "chkbx.calendar.onoff", formLayout, onKeys, onValues);
			calendarEl.setElementCssClass("o_sel_course_options_calendar");
			calendarEl.select("xx", calendarEnabled);
			calendarEl.setEnabled(editable && !managedCal);
			
			if(managedCal && calendarEnabled) {
				canHideToolbar &= false;
			}
		}
		
		boolean participantListEnabled = courseConfig.isParticipantListEnabled();
		boolean managedList = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.participantList);
		participantListEl = uifactory.addCheckboxesHorizontal("listIsOn", "chkbx.participantlist.onoff", formLayout, onKeys, onValues);
		participantListEl.select(onKeys[0], participantListEnabled);
		participantListEl.setEnabled(editable && !managedList);
		if(managedList && participantListEnabled) {
			canHideToolbar &= false;
		}
		
		boolean participantInfoEnabled = courseConfig.isParticipantInfoEnabled();
		boolean managedInfo = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.participantInfo);
		participantInfoEl = uifactory.addCheckboxesHorizontal("infoIsOn", "chkbx.participantinfo.onoff", formLayout, onKeys, onValues);
		participantInfoEl.select(onKeys[0], participantInfoEnabled);
		participantInfoEl.setEnabled(editable && !managedInfo);
		if(managedInfo && participantInfoEnabled) {
			canHideToolbar &= false;
		}

		boolean emailEnabled = courseConfig.isEmailEnabled();
		boolean managedEmail = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.email);
		emailEl = uifactory.addCheckboxesHorizontal("emailIsOn", "chkbx.email.onoff", formLayout, onKeys, onValues);
		emailEl.select(onKeys[0], emailEnabled);
		emailEl.setEnabled(editable && !managedEmail);
		if(managedEmail && emailEnabled) {
			canHideToolbar &= false;
		}
		boolean blogEnabled = courseConfig.isBlogEnabled();
		boolean managedBlog = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.blog);
		blogEl = uifactory.addCheckboxesHorizontal("blogIsOn", "chkbx.blog.onoff", formLayout, onKeys, onValues);
		blogEl.addActionListener(FormEvent.ONCHANGE);
		blogEl.select(onKeys[0], blogEnabled);
		blogEl.setEnabled(editable && !managedBlog);
		if(managedBlog && blogEnabled) {
			canHideToolbar &= false;
		}
		
		blogCont = FormLayoutContainer.createButtonLayout("blogButtons", getTranslator());
		blogCont.setRootForm(mainForm);
		formLayout.add(blogCont);
		blogOpenLink = uifactory.addFormLink("blog.not.selected", blogCont, Link.LINK);
		blogSelectLink = uifactory.addFormLink("blog.select", blogCont, Link.BUTTON_XSMALL);
		
		boolean wikiEnabled = courseConfig.isWikiEnabled();
		boolean managedwWiki = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.wiki);
		wikiEl = uifactory.addCheckboxesHorizontal("wikiIsOn", "chkbx.wiki.onoff", formLayout, onKeys, onValues);
		wikiEl.addActionListener(FormEvent.ONCHANGE);
		wikiEl.select(onKeys[0], wikiEnabled);
		wikiEl.setEnabled(editable && !managedwWiki);
		if(managedwWiki && wikiEnabled) {
			canHideToolbar &= false;
		}
		
		wikiCont = FormLayoutContainer.createButtonLayout("wikiButtons", getTranslator());
		wikiCont.setRootForm(mainForm);
		formLayout.add(wikiCont);
		wikiOpenLink = uifactory.addFormLink("wiki.not.selected", wikiCont, Link.LINK);
		wikiSelectLink = uifactory.addFormLink("wiki.select", wikiCont, Link.BUTTON_XSMALL);
		
		boolean forumEnabled = courseConfig.isForumEnabled();
		boolean managedForum = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.forum);
		forumEl = uifactory.addCheckboxesHorizontal("forumIsOn", "chkbx.forum.onoff", formLayout, onKeys, onValues);
		forumEl.select(onKeys[0], forumEnabled);
		forumEl.setEnabled(editable && !managedForum);
		if(managedForum && forumEnabled) {
			canHideToolbar &= false;
		}
		
		boolean documentsEnabled = courseConfig.isDocumentsEnabled();
		boolean managedDocuments = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.documents);
		documentsEl = uifactory.addCheckboxesHorizontal("documentsIsOn", "chkbx.documents.onoff", formLayout, onKeys, onValues);
		documentsEl.select(onKeys[0], documentsEnabled);
		documentsEl.setEnabled(editable && !managedDocuments);
		if(managedDocuments && documentsEnabled) {
			canHideToolbar &= false;
		}
		
		boolean chatEnabled = courseConfig.isChatEnabled();
		boolean managedChat = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.chat);
		chatEl = uifactory.addCheckboxesHorizontal("chatIsOn", "chkbx.chat.onoff", formLayout, onKeys, onValues);
		chatEl.select(onKeys[0], chatEnabled);
		chatEl.setEnabled(editable && !managedChat);
		if(managedChat && chatEnabled) {
			canHideToolbar &= false;
		}
		
		boolean glossaryEnabled = courseConfig.isGlossaryEnabled();
		boolean managedGlossary = RepositoryEntryManagedFlag.isManaged(entry, RepositoryEntryManagedFlag.glossary);
		glossaryEl = uifactory.addCheckboxesHorizontal("glossIsOn", "chkbx.glossary.onoff", formLayout, onKeys, onValues);
		glossaryEl.select(onKeys[0], glossaryEnabled);
		glossaryEl.setEnabled(editable && !managedGlossary);
		glossaryEl.setExampleKey("chkbx.glossary.explain", null);
		if(managedGlossary && glossaryEnabled && StringHelper.containsNonWhitespace(courseConfig.getGlossarySoftKey())) {
			canHideToolbar &= false;
		}

		toolbarEl.setEnabled(editable && canHideToolbar);
		
		FormLayoutContainer buttonsCont = FormLayoutContainer.createButtonLayout("buttons", getTranslator());
		buttonsCont.setRootForm(mainForm);
		formLayout.add(buttonsCont);
		uifactory.addFormCancelButton("cancel", buttonsCont, ureq, getWindowControl());
		FormSubmit saveButton = uifactory.addFormSubmitButton("save", buttonsCont);
		saveButton.setElementCssClass("o_sel_settings_save");
		saveButton.setEnabled(editable);
		
		updateUI();
	}

	private void updateUI() {
		boolean blogEnabled = blogEl.isSelected(0);
		blogOpenLink.setVisible(blogEnabled);
		blogCont.setVisible(blogEnabled);
		blogSelectLink.setVisible(blogEnabled);
		if (blogEnabled) {
			boolean blogSelected = blogEntry != null;
			blogOpenLink.setEnabled(blogSelected);
			String blogTitle = blogSelected
					? StringHelper.escapeHtml(blogEntry.getDisplayname())
					: translate("blog.not.selected");
			blogOpenLink.setI18nKey("blog.open", new String[] { blogTitle });
			blogOpenLink.setIconLeftCSS(blogSelected? "o_icon o_icon-fw o_icon_preview": null);

			boolean blogEntryEditable = blogEl.isEnabled() && blogEnabled;
			blogSelectLink.setVisible(blogEntryEditable);
			blogSelectLink.setI18nKey("blog.select.button", new String[] { translate(blogSelected? "blog.replace": "blog.select")});
		}

		boolean wikiEnabled = wikiEl.isSelected(0);
		wikiOpenLink.setVisible(wikiEnabled);
		wikiCont.setVisible(wikiEnabled);
		wikiSelectLink.setVisible(wikiEnabled);
		if (wikiEnabled) {
			boolean wikiSelected = wikiEntry != null;
			wikiOpenLink.setEnabled(wikiSelected);
			String wikiTitle = wikiSelected
					? StringHelper.escapeHtml(wikiEntry.getDisplayname())
					: translate("wiki.not.selected");
			wikiOpenLink.setI18nKey("wiki.open", new String[] { wikiTitle });
			wikiOpenLink.setIconLeftCSS(wikiSelected? "o_icon o_icon-fw o_icon_preview": null);

			boolean wikiEntryEditable = wikiEl.isEnabled() && wikiEnabled;
			wikiSelectLink.setVisible(wikiEntryEditable);
			wikiSelectLink.setI18nKey("wiki.select.button", new String[] { translate(wikiSelected? "wiki.replace": "wiki.select")});
		}
	}

	@Override
	protected void formInnerEvent(UserRequest ureq, FormItem source, FormEvent event) {
		if (source == blogEl) {
			updateUI();
		} else if (source == blogSelectLink) {
			doSelectBlog(ureq);
		} else if (source == wikiEl) {
			updateUI();
		} else if (source == wikiSelectLink) {
			doSelectWiki(ureq);
		} else if(toolbarEl == source) {
			if(!toolbarEl.isSelected(0) && isAnyToolSelected()) {
				showWarning("chkbx.toolbar.off.warning");
			}
			updateToolbar();
		}
		super.formInnerEvent(ureq, source, event);
	}
	
	private boolean isAnyToolSelected() {
		return searchEl.isSelected(0)
				|| (calendarEl != null && calendarEl.isSelected(0))
				|| participantListEl.isSelected(0)
				|| participantInfoEl.isSelected(0)
				|| emailEl.isSelected(0)
				|| blogEl.isSelected(0)
				|| wikiEl.isSelected(0)
				|| forumEl.isSelected(0)
				|| documentsEl.isSelected(0)
				|| chatEl.isSelected(0)
				|| glossaryEl.isSelected(0);
	}

	private void updateToolbar() {
		boolean enabled = toolbarEl.isSelected(0);
		explainEl.setVisible(enabled);
		searchEl.setVisible(enabled);
		if(calendarEl != null) {
			calendarEl.setVisible(enabled);
		}
		participantListEl.setVisible(enabled);
		participantInfoEl.setVisible(enabled);
		emailEl.setVisible(enabled);
		blogEl.setVisible(enabled);
		blogCont.setVisible(enabled);
		wikiEl.setVisible(enabled);
		wikiCont.setVisible(enabled);
		forumEl.setVisible(enabled);
		documentsEl.setVisible(enabled);
		chatEl.setVisible(enabled);
		glossaryEl.setVisible(enabled);
	}

	@Override
	protected void event(UserRequest ureq, Controller source, Event event) {
		if (source == blogSearchCtrl) {
			if (event == ReferencableEntriesSearchController.EVENT_REPOSITORY_ENTRY_SELECTED) {
				RepositoryEntry blogEntry = blogSearchCtrl.getSelectedEntry();
				if (blogEntry != null) {
					this.blogEntry = blogEntry;
					updateUI();
				}
			}
			cmc.deactivate();
			cleanUp();
		} else if (source == wikiSearchCtrl) {
			if (event == ReferencableEntriesSearchController.EVENT_REPOSITORY_ENTRY_SELECTED) {
				RepositoryEntry wikiEntry = wikiSearchCtrl.getSelectedEntry();
				if (wikiEntry != null) {
					this.wikiEntry = wikiEntry;
					updateUI();
				}
			}
			cmc.deactivate();
			cleanUp();
		}
		super.event(ureq, source, event);
	}

	private void cleanUp() {
		removeAsListenerAndDispose(blogSearchCtrl);
		removeAsListenerAndDispose(wikiSearchCtrl);
		removeAsListenerAndDispose(cmc);
		blogSearchCtrl = null;
		wikiSearchCtrl = null;
		cmc = null;
	}

	@Override
	protected boolean validateFormLogic(UserRequest ureq) {
		boolean allOk = true;
		
		blogCont.clearError();
		boolean blogEnabled = blogEl.isSelected(0);
		if (blogEnabled) {
			if (blogEntry == null) {
				blogCont.setErrorKey("error.no.blog.selected", null);
				allOk = false;
			}
		}

		wikiCont.clearError();
		boolean wikiEnabled = wikiEl.isSelected(0);
		if (wikiEnabled) {
			if (wikiEntry == null) {
				wikiCont.setErrorKey("error.no.wiki.selected", null);
				allOk = false;
			}
		}
		
		return allOk & super.validateFormLogic(ureq);
	}

	@Override
	protected void formOK(UserRequest ureq) {
		OLATResourceable courseOres = entry.getOlatResource();
		ICourse course = CourseFactory.openCourseEditSession(courseOres.getResourceableId());
		courseConfig = course.getCourseEnvironment().getCourseConfig();

		boolean toolbarEnabled = toolbarEl.isSelected(0);
		courseConfig.setToolbarEnabled(toolbarEnabled);
		
		boolean enableSearch = searchEl.isSelected(0);
		boolean updateSearch = courseConfig.isCourseSearchEnabled() != enableSearch;
		courseConfig.setCourseSearchEnabled(enableSearch && toolbarEnabled);
		
		boolean enableCalendar = calendarEl != null && calendarEl.isSelected(0);
		boolean updateCalendar = courseConfig.isCalendarEnabled() != enableCalendar && calendarModule.isEnableCourseToolCalendar();
		courseConfig.setCalendarEnabled(enableCalendar && toolbarEnabled);
		
		boolean enableParticipantList = participantListEl.isSelected(0);
		boolean updateParticipantList = courseConfig.isParticipantListEnabled() != enableParticipantList;
		courseConfig.setParticipantListEnabled(enableParticipantList && toolbarEnabled);
		
		boolean enableParticipantInfo = participantInfoEl.isSelected(0);
		boolean updateParticipantInfo = courseConfig.isParticipantInfoEnabled() != enableParticipantInfo;
		courseConfig.setParticipantInfoEnabled(enableParticipantInfo && toolbarEnabled);
		
		boolean enableEmail = emailEl.isSelected(0);
		boolean updateEmail = courseConfig.isEmailEnabled() != enableEmail;
		courseConfig.setEmailEnabled(enableEmail && toolbarEnabled);
		
		boolean enableBlog = blogEl.isSelected(0);
		boolean updateBlog = courseConfig.isBlogEnabled() != enableBlog;
		courseConfig.setBlogEnabled(enableBlog && toolbarEnabled);
		boolean blogSelected = enableBlog && blogEntry != null;
		String blogSoftKey = blogSelected? blogEntry.getSoftkey(): null;
		courseConfig.setBlogSoftKey(blogSoftKey);
		doUpdateBlogReference(course, blogSelected);
		
		boolean enableWiki = wikiEl.isSelected(0);
		boolean updateWiki = courseConfig.isWikiEnabled() != enableWiki;
		courseConfig.setWikiEnabled(enableWiki && toolbarEnabled);
		boolean wikiSelected = enableWiki && wikiEntry != null;
		String wikiSoftKey = wikiSelected? wikiEntry.getSoftkey(): null;
		courseConfig.setWikiSoftKey(wikiSoftKey);
		doUpdateWikiReference(course, wikiSelected);
		
		boolean enableForum = forumEl.isSelected(0);
		boolean updateForum = courseConfig.isForumEnabled() != enableForum;
		courseConfig.setForumEnabled(enableForum && toolbarEnabled);
		
		boolean enableDocuments = documentsEl.isSelected(0);
		boolean updateDocuments = courseConfig.isDocumentsEnabled() != enableDocuments;
		courseConfig.setDocumentsEnabled(enableDocuments && toolbarEnabled);
		
		boolean enableChat = chatEl.isSelected(0);
		boolean updateChat = courseConfig.isChatEnabled() != enableChat;
		courseConfig.setChatIsEnabled(enableChat && toolbarEnabled);
		
		boolean enableGlossary = glossaryEl != null && glossaryEl.isSelected(0);
		boolean updateGlossary = courseConfig.isGlossaryEnabled() != enableGlossary;
		courseConfig.setGlossaryIsEnabled(enableGlossary && toolbarEnabled);
		
		CourseFactory.setCourseConfig(course.getResourceableId(), courseConfig);
		CourseFactory.closeCourseEditSession(course.getResourceableId(), true);
		
		if(updateSearch) {
			ILoggingAction loggingAction = enableSearch ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_COURSESEARCH_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_COURSESEARCH_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.search, course.getResourceableId()), course);
		}
		
		if(updateCalendar) {
			ILoggingAction loggingAction = enableCalendar ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_CALENDAR_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_CALENDAR_DISABLED;

			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CalendarGUIModifiedEvent(), OresHelper.lookupType(CalendarManager.class));
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.calendar, course.getResourceableId()), course);
		}
		
		if(updateParticipantList) {
			ILoggingAction loggingAction = enableParticipantList ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_PARTICIPANTLIST_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_PARTICIPANTLIST_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.participantList, course.getResourceableId()), course);
		}

		if(updateParticipantInfo) {
			ILoggingAction loggingAction = enableParticipantInfo ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_PARTICIPANTINFO_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_PARTICIPANTINFO_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.participantInfo, course.getResourceableId()), course);
		}
		
		if(updateEmail) {
			ILoggingAction loggingAction = enableEmail ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_EMAIL_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_EMAIL_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.email, course.getResourceableId()), course);
		}
	
		if(updateBlog) {
			ILoggingAction loggingAction = enableBlog ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_BLOG_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_BLOG_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.blog, course.getResourceableId()), course);
		}
		
		if(updateWiki) {
			ILoggingAction loggingAction = enableWiki ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_WIKI_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_WIKI_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.wiki, course.getResourceableId()), course);
		}
		
		if(updateForum) {
			ILoggingAction loggingAction = enableForum ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_FORUM_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_FORUM_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.forum, course.getResourceableId()), course);
		}
		
		if(updateDocuments) {
			ILoggingAction loggingAction = enableDocuments ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_DOCUMENTS_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_DOCUMENTS_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.documents, course.getResourceableId()), course);
		}
		
		if(updateChat) {
			ILoggingAction loggingAction =enableChat ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_IM_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_IM_DISABLED;
			ThreadLocalUserActivityLogger.log(loggingAction, getClass());

			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.chat, course.getResourceableId()), course);
		}
		
		if(updateGlossary) {
			ILoggingAction loggingAction = enableCalendar ?
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_GLOSSARY_ENABLED:
					LearningResourceLoggingAction.REPOSITORY_ENTRY_PROPERTIES_GLOSSARY_DISABLED;

			ThreadLocalUserActivityLogger.log(loggingAction, getClass());
			CoordinatorManager.getInstance().getCoordinator().getEventBus()
				.fireEventToListenersOf(new CourseConfigEvent(CourseConfigType.glossary, course.getResourceableId()), course);
		}
		
		fireEvent(ureq, new ReloadSettingsEvent(false, false, true, false));
	}

	@Override
	protected void formCancelled(UserRequest ureq) {
		fireEvent(ureq, Event.CANCELLED_EVENT);
	}	

	private void doSelectBlog(UserRequest ureq) {
		blogSearchCtrl = new ReferencableEntriesSearchController(getWindowControl(), ureq, BlogFileResource.TYPE_NAME,
				translate("blog.select.titile"));
		listenTo(blogSearchCtrl);
		cmc = new CloseableModalController(getWindowControl(), translate("close"),
				blogSearchCtrl.getInitialComponent(), true, translate("blog.select.title"));
		cmc.activate();
	}

	private void doUpdateBlogReference(ICourse course, boolean blogSelected) {
		Optional<Reference> reference = referenceManager.getReferences(course).stream()
			.filter(ref -> ref.getUserdata().equals("blog"))
			.findAny();
		if (blogSelected) {
			if (reference.isPresent()) {
				if (!reference.get().getTarget().equals(blogEntry.getOlatResource())) {
					// User selected other blog (replaced)
					referenceManager.delete(reference.get());
					referenceManager.addReference(course, blogEntry.getOlatResource(), "blog");
				}
			} else {
				referenceManager.addReference(course, blogEntry.getOlatResource(), "blog");
			}
		} else if(!blogSelected && reference.isPresent()) {
			referenceManager.delete(reference.get());
		}
	}

	private void doSelectWiki(UserRequest ureq) {
		wikiSearchCtrl = new ReferencableEntriesSearchController(getWindowControl(), ureq, WikiResource.TYPE_NAME,
				translate("wiki.select.titile"));
		listenTo(wikiSearchCtrl);
		cmc = new CloseableModalController(getWindowControl(), translate("close"),
				wikiSearchCtrl.getInitialComponent(), true, translate("wiki.select.title"));
		cmc.activate();
	}

	private void doUpdateWikiReference(ICourse course, boolean wikiSelected) {
		Optional<Reference> reference = referenceManager.getReferences(course).stream()
			.filter(ref -> ref.getUserdata().equals("wiki"))
			.findAny();
		if (wikiSelected) {
			if (reference.isPresent()) {
				if (!reference.get().getTarget().equals(wikiEntry.getOlatResource())) {
					// User selected other wiki (replaced)
					referenceManager.delete(reference.get());
					referenceManager.addReference(course, wikiEntry.getOlatResource(), "wiki");
				}
			} else {
				referenceManager.addReference(course, wikiEntry.getOlatResource(), "wiki");
			}
		} else if(!wikiSelected && reference.isPresent()) {
			referenceManager.delete(reference.get());
		}
	}

}
