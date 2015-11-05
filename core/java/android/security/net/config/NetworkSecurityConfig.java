/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.security.net.config;

import android.util.ArraySet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.net.ssl.X509TrustManager;

/**
 * @hide
 */
public final class NetworkSecurityConfig {
    /** @hide */
    public static final boolean DEFAULT_CLEARTEXT_TRAFFIC_PERMITTED = true;
    /** @hide */
    public static final boolean DEFAULT_HSTS_ENFORCED = false;
    public static final NetworkSecurityConfig DEFAULT = getDefaultBuilder().build();

    private final boolean mCleartextTrafficPermitted;
    private final boolean mHstsEnforced;
    private final PinSet mPins;
    private final List<CertificatesEntryRef> mCertificatesEntryRefs;
    private Set<TrustAnchor> mAnchors;
    private final Object mAnchorsLock = new Object();
    private X509TrustManager mTrustManager;
    private final Object mTrustManagerLock = new Object();

    private NetworkSecurityConfig(boolean cleartextTrafficPermitted, boolean hstsEnforced,
            PinSet pins, List<CertificatesEntryRef> certificatesEntryRefs) {
        mCleartextTrafficPermitted = cleartextTrafficPermitted;
        mHstsEnforced = hstsEnforced;
        mPins = pins;
        mCertificatesEntryRefs = certificatesEntryRefs;
    }

    public Set<TrustAnchor> getTrustAnchors() {
        synchronized (mAnchorsLock) {
            if (mAnchors != null) {
                return mAnchors;
            }
            Set<TrustAnchor> anchors = new ArraySet<TrustAnchor>();
            for (CertificatesEntryRef ref : mCertificatesEntryRefs) {
                anchors.addAll(ref.getTrustAnchors());
            }
            mAnchors = anchors;
            return anchors;
        }
    }

    public boolean isCleartextTrafficPermitted() {
        return mCleartextTrafficPermitted;
    }

    public boolean isHstsEnforced() {
        return mHstsEnforced;
    }

    public PinSet getPins() {
        return mPins;
    }

    public X509TrustManager getTrustManager() {
        synchronized(mTrustManagerLock) {
            if (mTrustManager == null) {
                mTrustManager = new NetworkSecurityTrustManager(this);
            }
            return mTrustManager;
        }
    }

    void onTrustStoreChange() {
        synchronized (mAnchorsLock) {
            mAnchors = null;
        }
    }

    /**
     * Return a {@link Builder} for the default {@code NetworkSecurityConfig}.
     *
     * <p>
     * The default configuration has the following properties:
     * <ol>
     * <li>Cleartext traffic is permitted.</li>
     * <li>HSTS is not enforced.</li>
     * <li>No certificate pinning is used.</li>
     * <li>The system and user added trusted certificate stores are trusted for connections.</li>
     * </ol>
     *
     * @hide
     */
    public static final Builder getDefaultBuilder() {
        return new Builder()
                .setCleartextTrafficPermitted(DEFAULT_CLEARTEXT_TRAFFIC_PERMITTED)
                .setHstsEnforced(DEFAULT_HSTS_ENFORCED)
                // System certificate store, does not bypass static pins.
                .addCertificatesEntryRef(
                        new CertificatesEntryRef(SystemCertificateSource.getInstance(), false))
                // User certificate store, does not bypass static pins.
                .addCertificatesEntryRef(
                        new CertificatesEntryRef(UserCertificateSource.getInstance(), false));
    }

    /**
     * Builder for creating {@code NetworkSecurityConfig} objects.
     * @hide
     */
    public static final class Builder {
        private List<CertificatesEntryRef> mCertificatesEntryRefs;
        private PinSet mPinSet;
        private boolean mCleartextTrafficPermitted = DEFAULT_CLEARTEXT_TRAFFIC_PERMITTED;
        private boolean mHstsEnforced = DEFAULT_HSTS_ENFORCED;
        private boolean mCleartextTrafficPermittedSet = false;
        private boolean mHstsEnforcedSet = false;
        private Builder mParentBuilder;

        /**
         * Sets the parent {@code Builder} for this {@code Builder}.
         * The parent will be used to determine values not configured in this {@code Builder}
         * in {@link Builder#build()}, recursively if needed.
         */
        public Builder setParent(Builder parent) {
            // Sanity check to avoid adding loops.
            Builder current = parent;
            while (current != null) {
                if (current == this) {
                    throw new IllegalArgumentException("Loops are not allowed in Builder parents");
                }
                current = current.getParent();
            }
            mParentBuilder = parent;
            return this;
        }

        public Builder getParent() {
            return mParentBuilder;
        }

        public Builder setPinSet(PinSet pinSet) {
            mPinSet = pinSet;
            return this;
        }

        private PinSet getEffectivePinSet() {
            if (mPinSet != null) {
                return mPinSet;
            }
            if (mParentBuilder != null) {
                return mParentBuilder.getEffectivePinSet();
            }
            return PinSet.EMPTY_PINSET;
        }

        public Builder setCleartextTrafficPermitted(boolean cleartextTrafficPermitted) {
            mCleartextTrafficPermitted = cleartextTrafficPermitted;
            mCleartextTrafficPermittedSet = true;
            return this;
        }

        private boolean getEffectiveCleartextTrafficPermitted() {
            if (mCleartextTrafficPermittedSet) {
                return mCleartextTrafficPermitted;
            }
            if (mParentBuilder != null) {
                return mParentBuilder.getEffectiveCleartextTrafficPermitted();
            }
            return DEFAULT_CLEARTEXT_TRAFFIC_PERMITTED;
        }

        public Builder setHstsEnforced(boolean hstsEnforced) {
            mHstsEnforced = hstsEnforced;
            mHstsEnforcedSet = true;
            return this;
        }

        private boolean getEffectiveHstsEnforced() {
            if (mHstsEnforcedSet) {
                return mHstsEnforced;
            }
            if (mParentBuilder != null) {
                return mParentBuilder.getEffectiveHstsEnforced();
            }
            return DEFAULT_HSTS_ENFORCED;
        }

        public Builder addCertificatesEntryRef(CertificatesEntryRef ref) {
            if (mCertificatesEntryRefs == null) {
                mCertificatesEntryRefs = new ArrayList<CertificatesEntryRef>();
            }
            mCertificatesEntryRefs.add(ref);
            return this;
        }

        public Builder addCertificatesEntryRefs(Collection<? extends CertificatesEntryRef> refs) {
            if (mCertificatesEntryRefs == null) {
                mCertificatesEntryRefs = new ArrayList<CertificatesEntryRef>();
            }
            mCertificatesEntryRefs.addAll(refs);
            return this;
        }

        private List<CertificatesEntryRef> getEffectiveCertificatesEntryRefs() {
            if (mCertificatesEntryRefs != null) {
                return mCertificatesEntryRefs;
            }
            if (mParentBuilder != null) {
                return mParentBuilder.getEffectiveCertificatesEntryRefs();
            }
            return Collections.<CertificatesEntryRef>emptyList();
        }

        public boolean hasCertificateEntryRefs() {
            return mCertificatesEntryRefs != null;
        }

        public NetworkSecurityConfig build() {
            boolean cleartextPermitted = getEffectiveCleartextTrafficPermitted();
            boolean hstsEnforced = getEffectiveCleartextTrafficPermitted();
            PinSet pinSet = getEffectivePinSet();
            List<CertificatesEntryRef> entryRefs = getEffectiveCertificatesEntryRefs();
            return new NetworkSecurityConfig(cleartextPermitted, hstsEnforced, pinSet, entryRefs);
        }
    }
}
