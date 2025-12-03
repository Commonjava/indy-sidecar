/**
 * Copyright (C) 2011-2022 Red Hat, Inc. (https://github.com/Commonjava/indy-sidecar)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.util.sidecar.ftest.mock;

import org.commonjava.util.sidecar.services.ArchiveRetrieveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
public class BuildConfigIdEnvMock
                extends ArchiveRetrieveService
{
    @Override
    public String getBuildConfigId()
    {
        return "2000";
    }
}
