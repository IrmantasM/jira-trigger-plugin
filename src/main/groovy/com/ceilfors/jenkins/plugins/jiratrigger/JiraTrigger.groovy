package com.ceilfors.jenkins.plugins.jiratrigger

import com.atlassian.jira.rest.client.api.AddressableEntity
import com.atlassian.jira.rest.client.api.domain.Issue
import com.ceilfors.jenkins.plugins.jiratrigger.jira.JiraClient
import com.ceilfors.jenkins.plugins.jiratrigger.parameter.IssueAttributePathParameterMapping
import com.ceilfors.jenkins.plugins.jiratrigger.parameter.ParameterMapping
import com.ceilfors.jenkins.plugins.jiratrigger.parameter.ParameterResolver
import groovy.util.logging.Log
import hudson.model.*
import hudson.triggers.Trigger
import hudson.triggers.TriggerDescriptor
import jenkins.model.Jenkins
import org.kohsuke.stapler.DataBoundSetter

import javax.inject.Inject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level

/**
 * @author ceilfors
 */
@Log
abstract class JiraTrigger<T> extends Trigger<AbstractProject> {

    int quietPeriod

    @DataBoundSetter
    String jqlFilter = ""

    @DataBoundSetter
    List<ParameterMapping> parameterMappings = []

    final boolean run(Issue issue, T t) {
        log.fine("[${job.fullName}] - Processing ${issue.key} - ${getId(t)}")

        if (!filter(issue, t)) {
            return false
        }
        if (jqlFilter) {
            if (!jiraTriggerDescriptor.jiraClient.validateIssueKey(issue.key, jqlFilter)) {
                log.fine("[${job.fullName}] - Not scheduling build: The issue ${issue.key} doesn't match with the jqlFilter [$jqlFilter]")
                return false
            }
        }

        List<Action> actions = []
        if (parameterMappings) {
            actions << new ParametersAction(collectParameterValues(issue))
        }
        actions << new JiraIssueEnvironmentContributingAction(issue: issue)
        log.fine("[${job.fullName}] - Scheduling build for ${issue.key} - ${getId(t)}")
        return job.scheduleBuild(quietPeriod, getCause(issue, t), *actions)
    }

    @Override
    void start(AbstractProject project, boolean newInstance) {
        super.start(project, newInstance)
        jiraTriggerDescriptor.addTrigger(this)
    }

    @Override
    void stop() {
        super.stop()
        jiraTriggerDescriptor.removeTrigger(this)
    }

    AbstractProject getJob() {
        super.job
    }

    abstract boolean filter(Issue issue, T t)

    protected List<ParameterValue> collectParameterValues(Issue issue) {
        return parameterMappings.collect {
            if (it instanceof IssueAttributePathParameterMapping) {
                try {
                    return jiraTriggerDescriptor.parameterResolver.resolve(issue, it)
                } catch (JiraTriggerException e) {
                    log.log(Level.WARNING, "Can't resolve attribute ${it.issueAttributePath} from JIRA issue. Example: description, key, status.name. Read help for more information.", e)
                    return null
                }
            } else {
                throw new UnsupportedOperationException("Unsupported parameter mapping ${it.class}")
            }
        } - null
    }

    private String getId(T t) {
        if (t instanceof AddressableEntity) {
            return (t as AddressableEntity).self
        } else {
            return t.toString()
        }
    }

    JiraTriggerDescriptor getJiraTriggerDescriptor() {
        return super.getDescriptor() as JiraTriggerDescriptor
    }

    abstract Cause getCause(Issue issue, T t)

    @Log
    static abstract class JiraTriggerDescriptor extends TriggerDescriptor {

        @Inject
        protected Jenkins jenkins

        @Inject
        protected JiraClient jiraClient

        @Inject
        protected ParameterResolver parameterResolver

        private transient final List<JiraTrigger> triggers = new CopyOnWriteArrayList<>()

        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject
        }

        @SuppressWarnings("GroovyUnusedDeclaration") // Jenkins jelly
        public List<ParameterMapping.ParameterMappingDescriptor> getParameterMappingDescriptors() {
            return jenkins.getDescriptorList(ParameterMapping)
        }

        protected void addTrigger(JiraTrigger jiraTrigger) {
            triggers.add(jiraTrigger)
            log.finest("Added [${jiraTrigger.job.fullName}]:[${jiraTrigger.class.simpleName}] to triggers list")
        }

        protected void removeTrigger(JiraTrigger jiraTrigger) {
            def result = triggers.remove(jiraTrigger)
            if (result) {
                log.finest("Removed [${jiraTrigger.job.fullName}]:[${jiraTrigger.class.simpleName}] from triggers list")
            } else {
                log.warning(
                        "Bug! Failed to remove [${jiraTrigger.job.fullName}]:[${jiraTrigger.class.simpleName}] from triggers list. " +
                        "The job might accidentally be triggered by JIRA. Restart Jenkins to recover.")
            }
        }

        List<JiraTrigger> allTriggers() {
            return Collections.unmodifiableList(triggers)
        }
    }
}
