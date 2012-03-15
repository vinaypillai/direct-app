/*
 * Copyright (C) 2011 - 2012 TopCoder Inc., All Rights Reserved.
 */
package com.topcoder.direct.services.view.action.project.edit;


import com.topcoder.direct.services.project.metadata.entities.dao.DirectProjectMetadata;
import com.topcoder.direct.services.project.metadata.entities.dao.DirectProjectMetadataKey;
import com.topcoder.direct.services.view.action.FormAction;
import com.topcoder.direct.services.view.action.contest.launch.BaseDirectStrutsAction;
import com.topcoder.direct.services.view.dto.project.edit.ProjectMetadataOperation;
import com.topcoder.direct.services.view.form.SaveProjectSettingsForm;
import com.topcoder.direct.services.view.util.DirectUtils;
import com.topcoder.management.resource.ResourceRole;
import com.topcoder.security.TCSubject;
import com.topcoder.service.permission.Permission;
import com.topcoder.service.permission.ProjectPermission;
import com.topcoder.service.project.ProjectData;
import com.topcoder.shared.security.AuthorizationException;

import java.util.*;

/**
 * <p>
 * The action handles the operation of saving project settings and all the ajax operations take in Edit Project
 * Settings page.
 * </p>
 * 
 * <p>
 *     Version 1.1 (Release Assembly - TC Cockpit Edit Project and Project General Info Update) change notes:
 *     - Update method ${@link #executeAction()} to add ProjectMetadataOperation for new added project ratings.
 * </p>
 *
 * <p>
  *     Version 1.2 (Release Assembly - TC Direct Cockpit Release Two) change notes:
  *     - Add methods to assign full permissions for the client managers / topcoder managers when add or update in edit page.
  * </p>
 *
 * @author GreatKevin, TCSASSEMBLER
 * @version 1.2
 */
public class SaveCockpitProjectSettingAction extends BaseDirectStrutsAction implements FormAction<SaveProjectSettingsForm> {


    /**
     * Constant to represent the permission type id of full permission on project.
     * @since 1.2
     */
    private static final long PROJECT_FULL_PERMISSION_TYPE_ID = 3L;

    /**
     * Constant to represent the permission type name of full permission on project.
     * @since 1.2
     */
    private static final String PROJECT_FULL_PERMISSION_TYPE = "full";

    /**
     * Constant to represent the project metadata key id for client managers.
     * @since 1.2
     */
    private static final long CLIENT_MANAGER_METADATA_KEY = 1L;

    /**
     * Constant to represent the project metadata key id for topcoder managers.
     * @since 1.2
     */
    private static final long TOPCODER_MANAGER_METADATA_KEY = 2L;

    /**
     * The form of saving project settings.
     */
    private SaveProjectSettingsForm formData = new SaveProjectSettingsForm();

    /**
     * Gets form data.
     *
     * @return the form data.
     */
    public SaveProjectSettingsForm getFormData() {
        return formData;
    }

    /**
     * Sets the form data.
     *
     * @param formData the form data to set.
     */
    public void setFormData(SaveProjectSettingsForm formData) {
        this.formData = formData;
    }

    /**
     * Main logic of action execution.
     *
     * @throws Exception if any error.
     */
    @Override
    protected void executeAction() throws Exception {

        Map<String, String> result = new HashMap<String, String>();
        TCSubject currentUser = DirectUtils.getTCSubjectFromSession();

        List<ProjectMetadataOperation> metadataOperations = new ArrayList<ProjectMetadataOperation>();
        metadataOperations.add(getFormData().getBudget());
        metadataOperations.add(getFormData().getDuration());
        metadataOperations.add(getFormData().getSvn());
        metadataOperations.add(getFormData().getJira());
        metadataOperations.add(getFormData().getPrivacy());

        if (getFormData().getProjectRatings() != null && getFormData().getProjectRatings().size() > 0) {
            for (ProjectMetadataOperation op : getFormData().getProjectRatings()) {
                metadataOperations.add(op);
            }
        }

        if (getFormData().getCustomMetadataValues() != null && getFormData().getCustomMetadataValues().size() > 0) {
            for (ProjectMetadataOperation op : getFormData().getCustomMetadataValues()) {
                metadataOperations.add(op);
            }
        }

        persistAllProjectMetadata(metadataOperations, currentUser.getUserId());

        ProjectData project = getProjectServiceFacade().getProject(currentUser, getFormData().getProjectId());

        project.setName(formData.getProjectName());
        project.setDescription(formData.getProjectDescription());
        project.setProjectStatusId(formData.getProjectStatusId());


        getProjectServiceFacade().updateProject(currentUser, project);

        result.put("save project setting", "success");

        setResult(result);
    }

    /**
     * Saves all the project metadata in one batch.
     *
     * @param operations the project metadata operations could be update / add / remove.
     * @param userId     the id of the user.
     * @throws Exception if any error.
     */
    private void persistAllProjectMetadata(List<ProjectMetadataOperation> operations, long userId) throws Exception {

        List<DirectProjectMetadata> updateOrCreate = new ArrayList<DirectProjectMetadata>();
        List<Long> remove = new ArrayList<Long>();

        for (ProjectMetadataOperation op : operations) {

            if (op == null) continue;

            String action = op.getOperation().toLowerCase();
            DirectProjectMetadata metadata = new DirectProjectMetadata();
            if (action.equals(ProjectMetadataOperation.REMOVE)) {
                remove.add(op.getId());
                continue;
            } else if (action.equals(ProjectMetadataOperation.UPDATE)) {
                metadata.setId(op.getId());
            }

            metadata.setMetadataValue(op.getValue());
            metadata.setTcDirectProjectId(getFormData().getProjectId());
            DirectProjectMetadataKey key = getMetadataKeyService().getProjectMetadataKey(op.getKeyId());
            metadata.setProjectMetadataKey(key);
            updateOrCreate.add(metadata);
        }

        // delete first
        for (Long idToDelete : remove) {
            getMetadataService().deleteProjectMetadata(idToDelete, userId);
        }

        // add and update
        getMetadataService().saveProjectMetadata(updateOrCreate, userId);

    }

    /**
     * Creates a new <code>ProjectPermission</code> instance for updating permission.
     *
     * @param permission the permission type name
     * @param userId the user id
     * @param userPermissionId the user permission id, -1 for creating a new permission.
     * @return the new created <code>ProjectPermission</code> instance.
     * @since 1.2
     */
    private ProjectPermission getProjectPermission(String permission,
                                                   long userId, long userPermissionId) {
        ProjectPermission projectPermission = new ProjectPermission();
        projectPermission.setPermission(permission);
        projectPermission.setProjectId(getFormData().getProjectId());
        projectPermission.setUserId(userId);
        projectPermission.setStudio(false);
        projectPermission.setUserPermissionId(userPermissionId);

        return projectPermission;
    }

    /**
     * Updates the client managers or topcoder managers permissions to make sure all the managers have full permission
     * on the project.
     *
     * @param managersMetadata the metadata list contains the user id of the managers.
     * @param currentUser current user
     * @throws Exception if an expected error occurs.
     * @since 1.2
     */
    private void updateManagerPermissions(List<DirectProjectMetadata> managersMetadata, TCSubject currentUser) throws Exception {
        Set<Long> managerIds = new HashSet<Long>();
        Set<Long> managersToUpdateSet = new HashSet<Long>();

        for (DirectProjectMetadata meta : managersMetadata) {
            managerIds.add(Long.parseLong(meta.getMetadataValue()));
        }

        if (getPermissionServiceFacade() == null) {
            throw new IllegalStateException("The permission service is not initialized.");
        }


        // check if any existing client managers does not have write permission set on project
        final List<Permission> allPermissionsOfProject = getPermissionServiceFacade().getPermissionsByProject(currentUser, getFormData().getProjectId());

        // check current user has full permission to proceed
        boolean hasFullPermission = false;

        for (Permission p : allPermissionsOfProject) {
            if (p.getUserId() == currentUser.getUserId() && p.getPermissionType().getPermissionTypeId() == PROJECT_FULL_PERMISSION_TYPE_ID) {
                hasFullPermission = true;
            }
        }

        if (!hasFullPermission) {
            // do not have permission
            throw new AuthorizationException("You don't have FULL permission on this project to do this operation");
        }

        List<ProjectPermission> permissionToUpdate = new ArrayList<ProjectPermission>();

        // find the permission need to update: client manager has permission but it's not full permission
        for (Permission p : allPermissionsOfProject) {
            // update client manager's permission to full if it's not
            if (managerIds.contains(p.getUserId()) && p.getPermissionType().getPermissionTypeId() != PROJECT_FULL_PERMISSION_TYPE_ID) {
                permissionToUpdate.add(getProjectPermission(PROJECT_FULL_PERMISSION_TYPE, p.getUserId(), p.getPermissionId()));
                managersToUpdateSet.add(p.getUserId());
            }
        }

        for (Long userId : managerIds) {
            if (!managersToUpdateSet.contains(userId)) {
                // client manager does not have any permission, add new one
                permissionToUpdate.add(getProjectPermission(PROJECT_FULL_PERMISSION_TYPE, userId, -1L));
            }
        }

        // update the project permission
        getPermissionServiceFacade().updateProjectPermissions(currentUser,
                permissionToUpdate, ResourceRole.RESOURCE_ROLE_OBSERVER_ID);
    }

    /**
     * Handles the save client project managers ajax request.
     *
     * @return result code.
     */
    public String saveClientProjectManagers() {
        try {
            Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
            TCSubject currentUser = DirectUtils.getTCSubjectFromSession();

            persistAllProjectMetadata(getFormData().getClientManagers(), currentUser.getUserId());

            List<DirectProjectMetadata> values = getMetadataService().getProjectMetadataByProjectAndKey(getFormData().getProjectId(), CLIENT_MANAGER_METADATA_KEY);

            updateManagerPermissions(values, currentUser);

            for (DirectProjectMetadata value : values) {
                Map<String, String> user = new HashMap<String, String>();
                user.put("userId", value.getMetadataValue());
                user.put("handle", getUserService().getUserHandle(Long.parseLong(value.getMetadataValue())));
                result.put(String.valueOf(value.getId()), user);
            }

            setResult(result);
        } catch (Throwable e) {
            if (getModel() != null) {
                setResult(e);
            }
        }

        return SUCCESS;
    }

    /**
     * Handles the save TopCoder project managers ajax request.
     *
     * @return result code.
     */
    public String saveTopCoderManagers() {
        try {
            Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
            TCSubject currentUser = DirectUtils.getTCSubjectFromSession();

            persistAllProjectMetadata(getFormData().getProjectManagers(), currentUser.getUserId());

            List<DirectProjectMetadata> values = getMetadataService().getProjectMetadataByProjectAndKey(getFormData().getProjectId(), TOPCODER_MANAGER_METADATA_KEY);

            updateManagerPermissions(values, currentUser);

            for (DirectProjectMetadata value : values) {
                Map<String, String> user = new HashMap<String, String>();
                user.put("userId", value.getMetadataValue());
                user.put("handle", getUserService().getUserHandle(Long.parseLong(value.getMetadataValue())));
                result.put(String.valueOf(value.getId()), user);
            }
            setResult(result);
        } catch (Throwable e) {
            if (getModel() != null) {
                setResult(e);
            }
        }

        return SUCCESS;
    }

    /**
     * Handles the add new custom metadata key ajax request.
     *
     * @return result code.
     */
    public String addNewCustomMetadataKey() {
        try {
            Map<String, String> result = new HashMap<String, String>();
            TCSubject currentUser = DirectUtils.getTCSubjectFromSession();

            long id = getMetadataKeyService().createProjectMetadataKey(getFormData().getNewCustomKey(), currentUser.getUserId());

            result.put("id", String.valueOf(id));
            result.put("single", String.valueOf(getFormData().getNewCustomKey().isSingle()));
            result.put("name", String.valueOf(getFormData().getNewCustomKey().getName()));

            setResult(result);

        } catch (Throwable e) {
            if (getModel() != null) {
                setResult(e);
            }
        }

        return SUCCESS;
    }
}
