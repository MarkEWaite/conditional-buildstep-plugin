/*
 * The MIT License
 *
 * Copyright (C) 2011 by Dominik Bartholdi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.conditionalbuildstep;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import jenkins.model.DependencyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkinsci.plugins.conditionalbuildstep.dependency.ConditionalDependencyGraphWrapper;
import org.jenkinsci.plugins.conditionalbuildstep.singlestep.SingleConditionalBuilder;
import org.jenkinsci.plugins.conditionalbuildstep.singlestep.SingleConditionalBuilder.SingleConditionalBuilderDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * A buildstep wrapping any number of other buildsteps, controlling their execution based on a defined condition.
 *
 * @author Dominik Bartholdi (imod)
 * @author Chris Johnson (cjo9900)
 */
public class ConditionalBuilder extends Builder implements DependencyDeclarer {
    private static Logger log = Logger.getLogger(ConditionalBuilder.class.getName());

    // retaining backward compatibility
    private transient String condition;
    private transient boolean invertCondition;

    private final BuildStepRunner runner;
    private RunCondition runCondition;
    private List<BuildStep> conditionalbuilders;

    /**
     * @deprecated  No longer needed as part of the Constructor
     *  Use {@link #ConditionalBuilder(RunCondition, BuildStepRunner, List)}
    */
    @Deprecated
    public ConditionalBuilder(RunCondition runCondition, final BuildStepRunner runner) {
        //List<Builder> builders = new ArrayList<Builder>();
        this(runCondition, runner, new ArrayList<BuildStep>());
    }

    @DataBoundConstructor
    public ConditionalBuilder(RunCondition runCondition, final BuildStepRunner runner, List<BuildStep> conditionalbuilders) {
        this.runner = runner;
        this.runCondition = runCondition;
        this.conditionalbuilders = conditionalbuilders;
    }
    public BuildStepRunner getRunner() {
        return runner;
    }

    public RunCondition getRunCondition() {
        return runCondition;
    }
    
    @Override
    public Collection getProjectActions(AbstractProject<?, ?> project) {
        final Collection projectActions = new ArrayList();
        for (BuildStep buildStep : getConditionalbuilders()) {
            projectActions.addAll(buildStep.getProjectActions(project));
        }
        return projectActions;
    }

    public List<BuildStep> getConditionalbuilders() {
        if(conditionalbuilders == null){
            conditionalbuilders = new ArrayList<BuildStep>();
        }
        return conditionalbuilders;
    }

    /**
     * Set the Conditional Builders
     *
     * @deprecated  No longer needed as part of the DataBoundConstructor
     */
    @Deprecated
    public void setConditionalbuilders(List<BuildStep> conditionalbuilders) {
        this.conditionalbuilders = conditionalbuilders;
    }

    @Override
    public boolean prebuild(final AbstractBuild<?, ?> build, final BuildListener listener) {
        return runner.prebuild(runCondition, new BuilderChain(getConditionalbuilders()), build, listener);
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        return runner.perform(runCondition, new BuilderChain(getConditionalbuilders()), build, launcher, listener);
    }

    public Object readResolve() {
        if (condition != null) {
            // retaining backward compatibility
            this.runCondition = new LegacyBuildstepCondition(condition, invertCondition);
        }
        return this;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            // No need for aggregation for matrix build with MatrixAggregatable
            // this is only supported for: {@link Publisher}, {@link JobProperty}, {@link BuildWrapper}
            return !SingleConditionalBuilder.PROMOTION_JOB_TYPE.equals(aClass.getCanonicalName());
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return Messages.multistepbuilder_displayName();
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        public List<? extends Descriptor<? extends BuildStep>> getBuilderDescriptors(AbstractProject<?, ?> project) {
            final SingleConditionalBuilderDescriptor singleConditionalStepDescriptor = Hudson.getInstance().getDescriptorByType(
                    SingleConditionalBuilderDescriptor.class);
            return singleConditionalStepDescriptor.getAllowedBuilders(project);
        }

        public DescriptorExtensionList<BuildStepRunner, BuildStepRunner.BuildStepRunnerDescriptor> getBuildStepRunners() {
            return BuildStepRunner.all();
        }

        public List<? extends Descriptor<? extends RunCondition>> getRunConditions() {
            return RunCondition.all();
        }

    }

    public void buildDependencyGraph(AbstractProject project, DependencyGraph graph) {
        for (BuildStep builder : getConditionalbuilders()) {
            if(builder instanceof DependencyDeclarer) {
                ((DependencyDeclarer)builder).buildDependencyGraph(
                        project, new ConditionalDependencyGraphWrapper(graph, runCondition, runner));
            }
        }
    }

}
