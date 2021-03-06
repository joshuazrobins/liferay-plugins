/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.opensocial.service;

import com.liferay.opensocial.model.GadgetClp;
import com.liferay.opensocial.model.OAuthConsumerClp;
import com.liferay.opensocial.model.OAuthTokenClp;

import com.liferay.portal.kernel.bean.PortletBeanLocatorUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayInputStream;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayOutputStream;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.ClassLoaderObjectInputStream;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.BaseModel;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Brian Wing Shun Chan
 */
public class ClpSerializer {
	public static String getServletContextName() {
		if (Validator.isNotNull(_servletContextName)) {
			return _servletContextName;
		}

		synchronized (ClpSerializer.class) {
			if (Validator.isNotNull(_servletContextName)) {
				return _servletContextName;
			}

			try {
				ClassLoader classLoader = ClpSerializer.class.getClassLoader();

				Class<?> portletPropsClass = classLoader.loadClass(
						"com.liferay.util.portlet.PortletProps");

				Method getMethod = portletPropsClass.getMethod("get",
						new Class<?>[] { String.class });

				String portletPropsServletContextName = (String)getMethod.invoke(null,
						"opensocial-portlet-deployment-context");

				if (Validator.isNotNull(portletPropsServletContextName)) {
					_servletContextName = portletPropsServletContextName;
				}
			}
			catch (Throwable t) {
				if (_log.isInfoEnabled()) {
					_log.info(
						"Unable to locate deployment context from portlet properties");
				}
			}

			if (Validator.isNull(_servletContextName)) {
				try {
					String propsUtilServletContextName = PropsUtil.get(
							"opensocial-portlet-deployment-context");

					if (Validator.isNotNull(propsUtilServletContextName)) {
						_servletContextName = propsUtilServletContextName;
					}
				}
				catch (Throwable t) {
					if (_log.isInfoEnabled()) {
						_log.info(
							"Unable to locate deployment context from portal properties");
					}
				}
			}

			if (Validator.isNull(_servletContextName)) {
				_servletContextName = "opensocial-portlet";
			}

			return _servletContextName;
		}
	}

	public static Object translateInput(BaseModel<?> oldModel) {
		Class<?> oldModelClass = oldModel.getClass();

		String oldModelClassName = oldModelClass.getName();

		if (oldModelClassName.equals(GadgetClp.class.getName())) {
			return translateInputGadget(oldModel);
		}

		if (oldModelClassName.equals(OAuthConsumerClp.class.getName())) {
			return translateInputOAuthConsumer(oldModel);
		}

		if (oldModelClassName.equals(OAuthTokenClp.class.getName())) {
			return translateInputOAuthToken(oldModel);
		}

		return oldModel;
	}

	public static Object translateInput(List<Object> oldList) {
		List<Object> newList = new ArrayList<Object>(oldList.size());

		for (int i = 0; i < oldList.size(); i++) {
			Object curObj = oldList.get(i);

			newList.add(translateInput(curObj));
		}

		return newList;
	}

	public static Object translateInputGadget(BaseModel<?> oldModel) {
		GadgetClp oldClpModel = (GadgetClp)oldModel;

		BaseModel<?> newModel = oldClpModel.getGadgetRemoteModel();

		newModel.setModelAttributes(oldClpModel.getModelAttributes());

		return newModel;
	}

	public static Object translateInputOAuthConsumer(BaseModel<?> oldModel) {
		OAuthConsumerClp oldClpModel = (OAuthConsumerClp)oldModel;

		BaseModel<?> newModel = oldClpModel.getOAuthConsumerRemoteModel();

		newModel.setModelAttributes(oldClpModel.getModelAttributes());

		return newModel;
	}

	public static Object translateInputOAuthToken(BaseModel<?> oldModel) {
		OAuthTokenClp oldClpModel = (OAuthTokenClp)oldModel;

		BaseModel<?> newModel = oldClpModel.getOAuthTokenRemoteModel();

		newModel.setModelAttributes(oldClpModel.getModelAttributes());

		return newModel;
	}

	public static Object translateInput(Object obj) {
		if (obj instanceof BaseModel<?>) {
			return translateInput((BaseModel<?>)obj);
		}
		else if (obj instanceof List<?>) {
			return translateInput((List<Object>)obj);
		}
		else {
			return obj;
		}
	}

	public static Object translateOutput(BaseModel<?> oldModel) {
		Class<?> oldModelClass = oldModel.getClass();

		String oldModelClassName = oldModelClass.getName();

		if (oldModelClassName.equals(
					"com.liferay.opensocial.model.impl.GadgetImpl")) {
			return translateOutputGadget(oldModel);
		}

		if (oldModelClassName.equals(
					"com.liferay.opensocial.model.impl.OAuthConsumerImpl")) {
			return translateOutputOAuthConsumer(oldModel);
		}

		if (oldModelClassName.equals(
					"com.liferay.opensocial.model.impl.OAuthTokenImpl")) {
			return translateOutputOAuthToken(oldModel);
		}

		return oldModel;
	}

	public static Object translateOutput(List<Object> oldList) {
		List<Object> newList = new ArrayList<Object>(oldList.size());

		for (int i = 0; i < oldList.size(); i++) {
			Object curObj = oldList.get(i);

			newList.add(translateOutput(curObj));
		}

		return newList;
	}

	public static Object translateOutput(Object obj) {
		if (obj instanceof BaseModel<?>) {
			return translateOutput((BaseModel<?>)obj);
		}
		else if (obj instanceof List<?>) {
			return translateOutput((List<Object>)obj);
		}
		else {
			return obj;
		}
	}

	public static Throwable translateThrowable(Throwable throwable) {
		if (_useReflectionToTranslateThrowable) {
			try {
				if (_classLoader == null) {
					_classLoader = (ClassLoader)PortletBeanLocatorUtil.locate(_servletContextName,
							"portletClassLoader");
				}

				UnsyncByteArrayOutputStream unsyncByteArrayOutputStream = new UnsyncByteArrayOutputStream();
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(unsyncByteArrayOutputStream);

				objectOutputStream.writeObject(throwable);

				objectOutputStream.flush();
				objectOutputStream.close();

				UnsyncByteArrayInputStream unsyncByteArrayInputStream = new UnsyncByteArrayInputStream(unsyncByteArrayOutputStream.unsafeGetByteArray(),
						0, unsyncByteArrayOutputStream.size());
				ObjectInputStream objectInputStream = new ClassLoaderObjectInputStream(unsyncByteArrayInputStream,
						_classLoader);

				throwable = (Throwable)objectInputStream.readObject();

				objectInputStream.close();

				return throwable;
			}
			catch (SecurityException se) {
				if (_log.isInfoEnabled()) {
					_log.info("Do not use reflection to translate throwable");
				}

				_useReflectionToTranslateThrowable = false;
			}
			catch (Throwable throwable2) {
				_log.error(throwable2, throwable2);

				return throwable2;
			}
		}

		Class<?> clazz = throwable.getClass();

		String className = clazz.getName();

		if (className.equals(PortalException.class.getName())) {
			return new PortalException();
		}

		if (className.equals(SystemException.class.getName())) {
			return new SystemException();
		}

		if (className.equals(
					"com.liferay.opensocial.DuplicateGadgetURLException")) {
			return new com.liferay.opensocial.DuplicateGadgetURLException();
		}

		if (className.equals(
					"com.liferay.opensocial.GadgetPortletCategoryNamesException")) {
			return new com.liferay.opensocial.GadgetPortletCategoryNamesException();
		}

		if (className.equals("com.liferay.opensocial.GadgetURLException")) {
			return new com.liferay.opensocial.GadgetURLException();
		}

		if (className.equals("com.liferay.opensocial.NoSuchGadgetException")) {
			return new com.liferay.opensocial.NoSuchGadgetException();
		}

		if (className.equals(
					"com.liferay.opensocial.NoSuchOAuthConsumerException")) {
			return new com.liferay.opensocial.NoSuchOAuthConsumerException();
		}

		if (className.equals("com.liferay.opensocial.NoSuchOAuthTokenException")) {
			return new com.liferay.opensocial.NoSuchOAuthTokenException();
		}

		return throwable;
	}

	public static Object translateOutputGadget(BaseModel<?> oldModel) {
		GadgetClp newModel = new GadgetClp();

		newModel.setModelAttributes(oldModel.getModelAttributes());

		newModel.setGadgetRemoteModel(oldModel);

		return newModel;
	}

	public static Object translateOutputOAuthConsumer(BaseModel<?> oldModel) {
		OAuthConsumerClp newModel = new OAuthConsumerClp();

		newModel.setModelAttributes(oldModel.getModelAttributes());

		newModel.setOAuthConsumerRemoteModel(oldModel);

		return newModel;
	}

	public static Object translateOutputOAuthToken(BaseModel<?> oldModel) {
		OAuthTokenClp newModel = new OAuthTokenClp();

		newModel.setModelAttributes(oldModel.getModelAttributes());

		newModel.setOAuthTokenRemoteModel(oldModel);

		return newModel;
	}

	private static Log _log = LogFactoryUtil.getLog(ClpSerializer.class);
	private static ClassLoader _classLoader;
	private static String _servletContextName;
	private static boolean _useReflectionToTranslateThrowable = true;
}