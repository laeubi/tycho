/*******************************************************************************
 * Copyright (c) 2008, 2022 Sonatype Inc. and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *    Christoph Läubrich - #462 - Delay Pom considered items to the final Target Platform calculation
 *******************************************************************************/
package org.eclipse.tycho.p2.util.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.director.Explanation;
import org.eclipse.equinox.internal.p2.director.Explanation.IUToInstall;
import org.eclipse.equinox.internal.p2.director.Explanation.MissingIU;
import org.eclipse.equinox.internal.p2.director.Projector;
import org.eclipse.equinox.internal.p2.director.QueryableArray;
import org.eclipse.equinox.internal.p2.director.SimplePlanner;
import org.eclipse.equinox.internal.p2.director.Slicer;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.tycho.core.shared.MavenLogger;
import org.eclipse.tycho.p2.target.ee.NoExecutionEnvironmentResolutionHints;
import org.eclipse.tycho.repository.util.StatusTool;

@SuppressWarnings("restriction")
public class ProjectorResolutionStrategy extends AbstractSlicerResolutionStrategy {

    public ProjectorResolutionStrategy(MavenLogger logger) {
        super(logger);
    }

    @Override
    protected Slicer newSlicer(IQueryable<IInstallableUnit> availableUnits, Map<String, String> properties) {
        Predicate<IInstallableUnit> acceptor = data.getIInstallableUnitAcceptor();
        return new Slicer(availableUnits, properties, false) {
            @Override
            protected boolean isApplicable(IInstallableUnit iu) {
                if (acceptor != null) {
                    return acceptor.test(iu);
                }
                return super.isApplicable(iu);
            }
        };
    }

    @Override
    protected boolean isSlicerError(MultiStatus slicerStatus) {
        return slicerStatus.matches(IStatus.ERROR | IStatus.CANCEL);
    }

    @Override
    public Collection<IInstallableUnit> resolve(Map<String, String> properties, IProgressMonitor monitor)
            throws ResolverException {
        List<IInstallableUnit> additionalUnits = new ArrayList<>();
        Projector projector;
        ExecutionEnvironmentResolutionHints eeHints = data.getEEResolutionHints();
        boolean hasNoExecutionEnvironment = eeHints instanceof NoExecutionEnvironmentResolutionHints;
        boolean failOnMissingRequirements = data.failOnMissingRequirements();
        while (true) {
            Map<String, String> newSelectionContext = SimplePlanner.createSelectionContext(properties);

            IQueryable<IInstallableUnit> slice = slice(properties, additionalUnits, monitor);

            Set<IInstallableUnit> seedUnits = new LinkedHashSet<>(data.getRootIUs());
            List<IRequirement> seedRequires = new ArrayList<>();
            if (data.getAdditionalRequirements() != null) {
                seedRequires.addAll(data.getAdditionalRequirements());
            }

            // force profile UIs to be used during resolution
            seedUnits.addAll(eeHints.getMandatoryUnits());
            seedRequires.addAll(eeHints.getMandatoryRequires());

            projector = new Projector(slice, newSelectionContext, new HashSet<IInstallableUnit>(), false);
            projector.encode(createUnitRequiring("tycho", seedUnits, seedRequires),
                    EMPTY_IU_ARRAY /* alreadyExistingRoots */,
                    new QueryableArray(EMPTY_IU_ARRAY) /* installedIUs */, seedUnits /* newRoots */, monitor);
            IStatus s = projector.invokeSolver(monitor);
            if (s.getSeverity() == IStatus.ERROR) {
                Set<Explanation> explanation = projector.getExplanation(new NullProgressMonitor()); // suppress "Cannot complete the request.  Generating details."
                if (!failOnMissingRequirements || hasNoExecutionEnvironment) {
                    List<IRequirement> missingRequirements = collectMissingRequirements(explanation,
                            hasNoExecutionEnvironment, failOnMissingRequirements);
                    if (missingRequirements.size() > 0) {
                        //only start a new resolve if we have collected additional requirements...
                        IInstallableUnit providing = createUnitProviding("tycho.unresolved.requirements",
                                missingRequirements);
                        if (providing.getProvidedCapabilities().size() > 0) {
                            //... and we could provide additional capabilities
                            additionalUnits.add(providing);
                            // start a new cycle
                            continue;
                        }
                    }
                }
                // log all transitive requirements which cannot be satisfied; this doesn't print the dependency chain from the seed to the units with missing requirements, so this is less useful than the "explanation" 
                logger.debug(StatusTool.collectProblems(s));
                explainProblems(explanation, MavenLogger::error);
                throw new ResolverException(
                        explanation.stream().map(Object::toString).collect(Collectors.joining("\n")),
                        newSelectionContext.toString(), StatusTool.findException(s));
            }
            break;
        }
        Collection<IInstallableUnit> newState = projector.extractSolution();

        // remove fake IUs from resolved state
        newState.removeAll(eeHints.getTemporaryAdditions());
        newState.removeAll(additionalUnits);
        if (logger.isExtendedDebugEnabled()) {
            logger.debug("Resolved IUs:\n" + ResolverDebugUtils.toDebugString(newState, false));
        }
        return newState;
    }

    //TODO handling of incomplete ....
    private List<IRequirement> collectMissingRequirements(Set<Explanation> explanation,
            boolean hasNoExecutionEnvironment, boolean failOnMissingRequirements) {
        List<IRequirement> missingRequirements = new ArrayList<>();
        for (Explanation exp : explanation) {
            if (exp instanceof IUToInstall) {
                //this is one of our root IUs, no need to handle this
                continue;
            }
            if (exp instanceof MissingIU) {
                MissingIU missingIU = (MissingIU) exp;
                if (isEERequirement(missingIU.req)) {
                    //a missing EE is fatal so break out ...
                    if (hasNoExecutionEnvironment) {
                        //..to start a new cycle...
                        return Collections.singletonList(missingIU.req);
                    }
                    //.. or completely fail...
                    return Collections.emptyList();
                }
                if (failOnMissingRequirements) {
                    continue;
                }
                logger.debug("Recording missing requirement for IU " + missingIU.iu + ": " + missingIU.req);
                missingRequirements.add(missingIU.req);
            } else {
                if (logger.isExtendedDebugEnabled()) {
                    logger.debug("Ignoring Explanation of type " + exp.getClass()
                            + " in computation of missing requirements: " + exp);
                }
            }
        }
        missingRequirements.forEach(data::addMissingRequirement);
        return missingRequirements;
    }

}
