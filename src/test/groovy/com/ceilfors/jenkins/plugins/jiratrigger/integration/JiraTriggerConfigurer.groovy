package com.ceilfors.jenkins.plugins.jiratrigger.integration

import com.ceilfors.jenkins.plugins.jiratrigger.JiraTrigger
import jenkins.model.Jenkins

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

/**
 * @author ceilfors
 */
abstract class JiraTriggerConfigurer {

    protected JenkinsRunner jenkinsRunner
    protected Jenkins instance
    protected String jobName

    public JiraTriggerConfigurer(JenkinsRunner jenkinsRunner, String jobName) {
        this.jenkinsRunner = jenkinsRunner
        this.instance = jenkinsRunner.instance
        this.jobName = jobName
    }

    void setJqlFilter(String jqlFilter) {
        JiraTriggerConfigurationPage configPage = configure()
        configPage.setJqlFilter(jqlFilter)
        configPage.save()

        JiraTrigger jiraTrigger = getTrigger()
        assertThat(jiraTrigger.jqlFilter, is(jqlFilter))
    }

    void addParameterMapping(String jenkinsParameter, String issueAttributePath) {
        JiraTrigger jiraTrigger = getTrigger()
        def originalParameterMappingSize = jiraTrigger.parameterMappings.size()

        JiraTriggerConfigurationPage configPage = configure()
        configPage.addParameterMapping(jenkinsParameter, issueAttributePath)
        configPage.save()

        jiraTrigger = getTrigger()
        assertThat("Parameter mapping is not added", jiraTrigger.parameterMappings.size(), equalTo(originalParameterMappingSize + 1))
        assertThat(jiraTrigger.parameterMappings.last().jenkinsParameter, is(jenkinsParameter))
        assertThat(jiraTrigger.parameterMappings.last().issueAttributePath, is(issueAttributePath))
    }

    abstract JiraTriggerConfigurationPage configure()

    abstract JiraTrigger getTrigger()
}
