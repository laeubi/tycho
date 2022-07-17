/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven.services;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Date;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.osgi.signedcontent.SignedContent;
import org.eclipse.osgi.signedcontent.SignedContentEntry;
import org.eclipse.osgi.signedcontent.SignedContentFactory;
import org.eclipse.osgi.signedcontent.SignerInfo;
import org.osgi.framework.Bundle;

@Component(role = SignedContentFactory.class)
public class DefaultSignedContentFactory implements SignedContentFactory {

	@Override
	public SignedContent getSignedContent(File content) throws IOException, InvalidKeyException, SignatureException,
			CertificateException, NoSuchAlgorithmException, NoSuchProviderException {
		return new SignedContent() {

			@Override
			public boolean isSigned() {
				return false;
			}

			@Override
			public SignerInfo getTSASignerInfo(SignerInfo signerInfo) {
				return null;
			}

			@Override
			public Date getSigningTime(SignerInfo signerInfo) {
				return null;
			}

			@Override
			public SignerInfo[] getSignerInfos() {
				return new SignerInfo[0];
			}

			@Override
			public SignedContentEntry getSignedEntry(String name) {
				return null;
			}

			@Override
			public SignedContentEntry[] getSignedEntries() {
				return new SignedContentEntry[0];
			}

			@Override
			public void checkValidity(SignerInfo signerInfo)
					throws CertificateExpiredException, CertificateNotYetValidException {

			}
		};
	}

	@Override
	public SignedContent getSignedContent(Bundle bundle) throws IOException, InvalidKeyException, SignatureException,
			CertificateException, NoSuchAlgorithmException, NoSuchProviderException {
		throw new UnsupportedOperationException();
	}

}
