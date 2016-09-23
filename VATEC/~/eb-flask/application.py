import os, re, csv, ast, csv
import math
import jinja2
import collections
import xml.etree.ElementTree as xml_parser
import urllib2
jinja_environment = jinja2.Environment(autoescape=True, loader=jinja2.FileSystemLoader(os.path.join(os.path.dirname(__file__), 'templates')))
from operator import itemgetter
import flask
from flask.ext.mysql import MySQL
#from flask.ext.sqlalchemy import SQLAlchemy
#in requirements.txt add Flask-SQLAlchemy==1.0

from flask import g


mysql = MySQL()
application = flask.Flask(__name__)


# application.config['MYSQL_DATABASE_USER'] = 'compact'
# application.config['MYSQL_DATABASE_PASSWORD'] = 'elixr_2013'
# application.config['MYSQL_DATABASE_DB'] = 'COMPACT'
# application.config['MYSQL_DATABASE_HOST'] = 'compactreplica.c0ho5hiaavc3.us-east-1.rds.amazonaws.com'
# application.config['MYSQL_DATABASE_PORT'] = 3306

application.config['MYSQL_DATABASE_USER'] = 'root'
application.config['MYSQL_DATABASE_PASSWORD'] = 'HeRa@CCI@FSU'
application.config['MYSQL_DATABASE_DB'] = 'cancer'
application.config['MYSQL_DATABASE_HOST'] = 'somelab12.cci.fsu.edu'
application.config['MYSQL_DATABASE_PORT'] = 3306

mysql.init_app(application)

#db = mysql.connect()

#Set application.debug=true to enable tracebacks on Beanstalk log output.
#Make sure to remove this line before deploying to production.
application.debug=True

csv_result = []

@application.route('/', methods=['POST','GET'])
def vatec_main():
    db = mysql.connect()
    cur = db.cursor()

    disease_sql = "select distinct task from cancer_cui"
    cur.execute(disease_sql)
    diseases_list = ()
    for row in cur.fetchall():
        diseases_list += (row[0],)

    if db.open:
        db.close()

    if diseases_list is None:
        return "Failed in retrieving the list of disease."
    else:
        return flask.render_template('index.html', diseases_list=diseases_list)

@application.route('/features',methods=['POST','GET'])
def show_frequent_features():
    topk = flask.request.form['topk']

    disease = flask.request.form['disease']
    typed_disease = flask.request.form['typed_disease']
    if(disease == '' and typed_disease !=''):
        disease = typed_disease

    db = mysql.connect()
    cur = db.cursor()

    num_trials_with_disease_sql = "select count(cui) from cancer_cui where task = '%s' and pattern != 'None' " %(disease)
    cur.execute(num_trials_with_disease_sql)
    num_trials_with_disease = ''
    for row in cur.fetchall():
        num_trials_with_disease = row[0]

    if topk == 'all':
        topk = '100000000000' # big enough  number
    frequent_numeric_feature_sql = "select cui,sty,cui_str,count(*) as freq from cancer_cui where task = '%s' and pattern != 'None' group by cui,sty order by freq desc limit %s " % (disease,topk)
    cur.execute(frequent_numeric_feature_sql)

    distribution_numeric_features = []
    for row in cur.fetchall():
        ###cui,sty,cui_str,freq
        distribution_numeric_features.append((row[2],row[1], row[0],row[3]))
    if db.open:
        db.close()
    return flask.render_template('index.html', **locals())




@application.route('/builder',methods=['POST','GET'])
def build_query():

    var, aggregator_output, minimum_value, maximum_value, lower, upper, value_range_verification_output, enrollment_value, value_spectrum_output, num_trials, phase_query, condition_query, query_used, phases, conditions, status_query, statuses, study_type_query, study_types, intervention_type_query, intervention_types, agency_type_query, agency_types, gender_query, gender, modal_boundary_output, enrollment_spectrum_output, num_of_trials_output, detail_enrollment_spectrum_output,value_spectrum_output_trial_ids, initial_value_spectrum, on_option_page, start_date_query, start_date, age_query, value_range_distribution_output,value_range_width_distribution_output, average_enrollment_spectrum_output, disease, intervention_model_query, allocation_query, intervention_models, allocations, time_perspective_query, time_perspectives, start_date_before = '', "", '', '', '', '', '', '', "", [], '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', "", "", "", "", "", "", '', '', '', '', "", "", "", '', '','','','','','',''

    disease = flask.request.form['disease']
    cuisty = str(flask.request.form['featureInfo']).strip().split()
    cui = cuisty[0]
    sty = cuisty[1]
    name = cuisty[2]
    #cur = mysql.connect().cursor()
    db = mysql.connect()
    cur = db.cursor()

    curr_condition = "task = '%s' and cui='%s' and sty = '%s' and pattern != 'None'"  %(disease, cui, sty)
    curr_tid = "SELECT distinct tid from cancer_cui where %s" %(curr_condition)
    # get number of trials of the selected disease
    sql1 = "SELECT count(cui) FROM cancer_cui where task = '%s' and cui='%s' and sty = '%s' and pattern != 'None' " %(disease, cui, sty)
    cur.execute(sql1)
    total_num = 0
    for row in cur.fetchall():
        total_num = int(row[0])
        aggregator_output += "There are <span style=color:red>%s</span> "  % (total_num) + " UMLS terms containing <span style=color:red>%s(%s)</span>" %(cui,sty) + "<br><br>"

    # get the minimum value and maximum value for the variable
    sql2 = "SELECT min(duration),max(duration) FROM cancer_cui where task = '%s' and cui='%s' and sty = '%s' and pattern != 'None' " %(disease, cui, sty)

    cur.execute(sql2)
    for row2 in cur.fetchall():
        minimum_value = row2[0]
        maximum_value = row2[1]

    # get distribution of study types
    distribution_study_type = {}
    sql3 = "SELECT T.study_type, count(*) FROM meta T WHERE T.tid in (SELECT tid from cancer_cui where %s)" %(curr_condition)  + " GROUP BY T.study_type"
    cur.execute(sql3)
    for row3 in cur.fetchall():
        study_type = row3[0]
        percentage_study_type = "%.2f" %(float(row3[1])/total_num * 100)
        distribution_study_type[study_type] = percentage_study_type

    if "Interventional" not in distribution_study_type:
        distribution_study_type["Interventional"] = "0"
    if "Observational" not in distribution_study_type:
        distribution_study_type["Observational"] = "0"
    if "Observational [Patient Registry]" not in distribution_study_type:
        distribution_study_type["Observational [Patient Registry]"] = "0"
    if "Expanded Access" not in distribution_study_type:
        distribution_study_type["Expanded Access"] = "0"

    # get distribution of intervention types
    distribution_intervention_type = {}
    sql4_all = "SELECT COUNT(DISTINCT TID) FROM meta T WHERE intervention_type !='' AND TID in (%s)" % (curr_tid)
    cur.execute(sql4_all)
    for row4_all in cur.fetchall():
        num_trials_with_intervention_type = int(row4_all[0])

    sql4 = "SELECT T.intervention_type, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) +" GROUP BY T.intervention_type"
    cur.execute(sql4)
    for row4 in cur.fetchall():
        intervention_type = row4[0]
        percentage_intervention_type = "%.2f" %(float(row4[1])/num_trials_with_intervention_type * 100)
        distribution_intervention_type[intervention_type] = percentage_intervention_type

    if "Drug" not in distribution_intervention_type:
        distribution_intervention_type["Drug"] = "0"
    if "Procedure" not in distribution_intervention_type:
        distribution_intervention_type["Procedure"] = "0"
    if "Biological" not in distribution_intervention_type:
        distribution_intervention_type["Biological"] = "0"
    if "Device" not in distribution_intervention_type:
        distribution_intervention_type["Device"] = "0"
    if "Behavioral" not in distribution_intervention_type:
        distribution_intervention_type["Behavioral"] = "0"
    if "Dietary Supplement" not in distribution_intervention_type:
        distribution_intervention_type["Dietary Supplement"] = "0"
    if "Genetic" not in distribution_intervention_type:
        distribution_intervention_type["Genetic"] = "0"
    if "Radiation" not in distribution_intervention_type:
        distribution_intervention_type["Radiation"] = "0"
    if "Other" not in distribution_intervention_type:
        distribution_intervention_type["Other"] = "0"

    # get distribution of status
    distribution_status = {}
    sql5 = "SELECT T.overall_status, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) + " GROUP BY T.overall_status"
    cur.execute(sql5)
    for row5 in cur.fetchall():
        status = row5[0]
        percentage_status = "%.2f" %(float(row5[1])/total_num * 100)
        distribution_status[status] = percentage_status

    if "Recruiting" not in distribution_status:
        distribution_status["Recruiting"] = "0"
    if "Not yet recruiting" not in distribution_status:
        distribution_status["Not yet recruiting"] = "0"
    if "Active, not recruiting" not in distribution_status:
        distribution_status["Active, not recruiting"] = "0"
    if "Completed" not in distribution_status:
        distribution_status["Completed"] = "0"
    if "Withdrawn" not in distribution_status:
        distribution_status["Withdrawn"] = "0"
    if "Suspended" not in distribution_status:
        distribution_status["Suspended"] = "0"
    if "Terminated" not in distribution_status:
        distribution_status["Terminated"] = "0"
    if "Enrolling by invitation" not in distribution_status:
        distribution_status["Enrolling by invitation"] = "0"

    # get distribution of sponsor type
    distribution_sponsor_type = {}
    sql6 = "SELECT T.agency_type, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) +" GROUP BY T.agency_type"
    cur.execute(sql6)
    for row6 in cur.fetchall():
        sponsor_type = row6[0]
        percentage_sponsor_type = "%.2f" %(float(row6[1])/total_num * 100)
        distribution_sponsor_type[sponsor_type] = percentage_sponsor_type

    if "['NIH']" not in distribution_sponsor_type:
        distribution_sponsor_type["['NIH']"] = "0"
    if "['Industry']" not in distribution_sponsor_type:
        distribution_sponsor_type["['Industry']"] = "0"
    if "['U.S. Fed']" not in distribution_sponsor_type:
        distribution_sponsor_type["['U.S. Fed']"] = "0"
    if "['Other']" not in distribution_sponsor_type:
        distribution_sponsor_type["['Other']"] = "0"


    # get distribution of phase
    distribution_phase = {}
    sql7 = "SELECT T.phase, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) + " GROUP BY T.phase"
    cur.execute(sql7)
    for row7 in cur.fetchall():
        phase = row7[0]
        percentage_phase = "%.2f" %(float(row7[1])/total_num * 100)
        distribution_phase[phase] = percentage_phase

    if "Phase 0" not in distribution_phase:
        distribution_phase["Phase 0"] = "0"
    if "Phase 1" not in distribution_phase:
        distribution_phase["Phase 1"] = "0"
    if "Phase 2" not in distribution_phase:
        distribution_phase["Phase 2"] = "0"
    if "Phase 3" not in distribution_phase:
        distribution_phase["Phase 3"] = "0"
    if "Phase 4" not in distribution_phase:
        distribution_phase["Phase 4"] = "0"
    if "N/A" not in distribution_phase:
        distribution_phase["N/A"] = "0"

    # get distribution of gender
    distribution_gender = {}
    sql8 = "SELECT T.gender, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) +" GROUP BY T.gender"
    cur.execute(sql8)
    for row8 in cur.fetchall():
        gender = row8[0]
        percentage_gender = "%.2f" %(float(row8[1])/total_num * 100)
        distribution_gender[gender] = percentage_gender

    if "both" not in distribution_gender:
        distribution_gender["both"] = "0"
    if "male" not in distribution_gender:
        distribution_gender["male"] = "0"
    if "female" not in distribution_gender:
        distribution_gender["female"] = "0"

    # get distribution of intervention model
    distribution_intervention_model = {}

    sql9 = "SELECT T.intervention_model, count(*) FROM meta T WHERE T.tid in (%s)" % (curr_tid) + " GROUP BY T.intervention_model"
    cur.execute(sql9)
    for row9 in cur.fetchall():
        intervention_model = row9[0]
        percentage_intervention_model = "%.2f" %(float(row9[1])/total_num * 100)
        distribution_intervention_model[intervention_model] = percentage_intervention_model

    if "Parallel Assignment" not in distribution_intervention_model:
        distribution_intervention_model["Parallel Assignment"] = "0"
    if "Factorial Assignment" not in distribution_intervention_model:
        distribution_intervention_model["Factorial Assignment"] = "0"
    if "Crossover Assignment" not in distribution_intervention_model:
        distribution_intervention_model["Crossover Assignment"] = "0"
    if "Single Group Assignment" not in distribution_intervention_model:
        distribution_intervention_model["Single Group Assignment"] = "0"
    if "N/A" not in distribution_intervention_model:
        distribution_intervention_model["N/A"] = "0"

    # get distribution of allocation
    distribution_allocation = {}

    sql10 = "SELECT T.allocation, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) + " GROUP BY T.allocation"
    cur.execute(sql10)
    for row10 in cur.fetchall():
        allocation = row10[0]
        percentage_allocation = "%.2f" %(float(row10[1])/total_num * 100)
        distribution_allocation[allocation] = percentage_allocation

    if "Randomized" not in distribution_allocation:
        distribution_allocation["Randomized"] = "0"
    if "Non-Randomized" not in distribution_allocation:
        distribution_allocation["Non-Randomized"] = "0"
    if "N/A" not in distribution_allocation:
        distribution_allocation["N/A"] = "0"

    # get distribution of time perspective
    distribution_time_perspective = {}

    sql11 = "SELECT T.time_perspective, count(*) FROM meta T WHERE T.tid in (%s)" %(curr_tid) +" GROUP BY T.time_perspective"
    cur.execute(sql11)
    for row11 in cur.fetchall():
        time_perspective = row11[0]
        percentage_time_perspective = "%.2f" %(float(row11[1])/total_num * 100)
        distribution_time_perspective[time_perspective] = percentage_time_perspective

    if "Cross-Sectional" not in distribution_time_perspective:
        distribution_time_perspective["Cross-Sectional"] = "0"
    if "Non-Longitudinal" not in distribution_time_perspective:
        distribution_time_perspective["Non-Longitudinal"] = "0"
    if "Prospective" not in distribution_time_perspective:
        distribution_time_perspective["Prospective"] = "0"
    if "Retrospective" not in distribution_time_perspective:
        distribution_time_perspective["Retrospective"] = "0"
    if "N/A" not in distribution_time_perspective:
        distribution_time_perspective["N/A"] = "0"

    # generate initial value specturm
    initial_value_spectrum, upper_value, lower_value = generate_initial_value_spectrum(cur, cui,sty, disease)

    if db.open:
        db.close()

    #return "Current disease is still %s." %disease + " Current variable is %s" %var
    return flask.render_template('query_builder.html', **locals())




@application.route('/analysis',methods=['POST','GET'])
def analysis():


    var, aggregator_output, minimum_value, maximum_value, lower, upper, value_range_verification_output, enrollment_value, value_spectrum_output, num_trials, phase_query, condition_query, query_used, phases, conditions, status_query, statuses, study_type_query, study_types, intervention_type_query, intervention_types, agency_type_query, agency_types, gender_query, gender, modal_boundary_output, enrollment_spectrum_output, num_of_trials_output, detail_enrollment_spectrum_output,value_spectrum_output_trial_ids, initial_value_spectrum, on_option_page, start_date_query, start_date, age_query, value_range_distribution_output,value_range_width_distribution_output, average_enrollment_spectrum_output, disease, intervention_model_query, allocation_query, intervention_models, allocations, time_perspective_query, time_perspectives, start_date_before, disease_query = '', "", '', '', '', '', '', '', "", [], '', '', '', '', '', '', '', '', '', '', '', '', '', '', '', "", "", "", "", "", "", '', '', '', '', "", "", "", '', '','','','','','','',''

    # define CSV outpout file
    global csv_result
    csv_result = []

    disease = flask.request.form['disease']
    featureName = flask.request.form['featureName']
    var = curr_condition = str(flask.request.form['variable']).strip()

    lower = flask.request.form.get('lower',None)
    upper = flask.request.form.get('upper',None)
    width_option = flask.request.form.get('width_option',False)
    fixed_width = flask.request.form.get('fixed_width',None)
    condition_option = flask.request.form.get('conditions',None)
    plot_type = flask.request.form.get('plot_type',None)
    start_month_option = flask.request.form.get('start_month',None)
    start_year_option = flask.request.form.get('start_year',None)
    start_month_before_option = flask.request.form.get('start_month_before',None)
    start_year_before_option = flask.request.form.get('start_year_before',None)
    minimum_age_option = flask.request.form.get('minimum_age',None)
    maximum_age_option = flask.request.form.get('maximum_age',None)
    gender = flask.request.form.get('gender',None)
    aggregate_analysis_type = flask.request.form.get('aggregate_analysis_type',None)


    phase_option = flask.request.form.getlist('phase', None)
    status_option = flask.request.form.getlist('status', None)
    study_type_option = flask.request.form.getlist('study_types', None)
    intervention_type_option = flask.request.form.getlist('intervention_types', None)
    agency_type_option = flask.request.form.getlist('agency_types', None)
    intervention_model_option = flask.request.form.getlist('intervention_model', None)
    allocation_option = flask.request.form.getlist('allocation', None)
    time_perspective_option = flask.request.form.getlist('time_perspective', None)

    conditions = condition_option

    # the list of trial IDs if the condition is not indexed in COMPACT
    trial_ids_with_conditions = []

    # retrieve the list of trial IDs associated with a disease, if not in the disease list, query CT.gov to retrieve trial IDs

    #cur = mysql.connect().cursor()

    db = mysql.connect()
    cur = db.cursor()

    disease_query = ""
    if (len(condition_option) != 0):
        list_of_diseases = []
        all_diseases_query = "select distinct task from cancer_cui"
        cur.execute(all_diseases_query)
        for row in cur.fetchall():
            list_of_diseases.append(row[0])

        if condition_option in list_of_diseases:
            # generate the part of query containing the condition field
            #disease_query = " T.tid in (select tid from all_diseases_trials where disease='%s')" %condition_option
            disease_query = " (V.task='%s')" %condition_option
        else:
            # trial_ids_with_conditions only deals with disease name not in the disease_list table or multiple diseases in the condition field
            disease_query = " (T.conditions LIKE '%%')"
            trial_ids_with_conditions = get_disease_clinical_trials(condition_option)
    else:
        disease_query = " (T.conditions LIKE '%%')"




    # check phase option and generate part of the query
    phase_query = ""
    if (len(phase_option) == 0):
        phase_query += " (T.phase LIKE '%%')"
    else:
        phase_query += " ("
        for phase in phase_option:
            phase_query += " T.phase LIKE '%s'" % (phase) +" OR"
            phases += phase + "/ "
        # this is not meaningful, just to terminate the part of the query
        phase_query += " T.phase LIKE '%terminated%')"

    # check status option and generate part of the query
    if (len(status_option) == 0):
        status_query += " (T.overall_status LIKE '%%')"
    else:
        status_query += " ("
        for status in status_option:
            if (status == "Open Studies"):
                status_query += " T.overall_status = 'Recruiting' OR T.overall_status = 'Not yet recruiting' OR"
            elif (status == "Closed Studies"):
                status_query += " T.overall_status = 'Active, not recruiting' OR T.overall_status = 'Active, not recruiting' OR T.overall_status = 'Completed' OR T.overall_status = 'Withdrawn' OR T.overall_status = 'Suspended' OR T.overall_status = 'Terminated' OR T.overall_status = 'Enrolling by invitation' OR"
            else:
                status_query += " T.overall_status = '%s'" % (status) + " OR"
            statuses += status + "/ "
        # this is not meaningful, just to terminate the part of the query
        status_query += " T.overall_status LIKE '%reterminated%')"

    # check study type option and generate part of the query
    if (len(study_type_option) == 0):
        study_type_query = " (T.study_type LIKE '%%')"
    else:
        study_type_query += " ("
        for study_type in study_type_option:
            study_type_query += " T.study_type = '%s'" %(study_type) +" OR"
            study_types += study_type + "/ "
        study_type_query += " T.study_type LIKE '%terminated%')"

    # check intervention type option and generate part of the query
    if (len(intervention_type_option) == 0):
        intervention_type_query = " (T.intervention_type LIKE '%%')"
    else:
        intervention_type_query += " ("
        for intervention_type in intervention_type_option:
            intervention_type_query += " T.intervention_type = '%s'" %(intervention_type) + " OR"
            intervention_types += intervention_type + "/ "
        intervention_type_query += " T.intervention_type LIKE '%terminated%')"

    # check agency type option and generate part of the query
    if (len(agency_type_option) == 0):
        agency_type_query = " (T.agency_type LIKE '%%')"
    else:
        agency_type_query += " ("
        for agency_type in agency_type_option:
            agency_type_query += " T.agency_type LIKE '%%%s%%'" %(agency_type) + " OR"
            agency_types += agency_type + "/ "
        agency_type_query += " T.agency_type LIKE '%terminated%')"

    # check agency type option and generate part of the query
    if (gender == '' or gender == 'all'):
        gender_query = " (T.gender LIKE '%%')"
    else:
        gender_query = " (T.gender = '%s'" %(gender) + ")"

    # check start_year_option start_month_option and generate start_date_query
    if (start_year_option == '' or start_month_option == '' or start_month_option == 'N/A'):
        start_date_query = " (T.start_date LIKE '%%')"
    else:
        start_date = str(start_month_option) + " " + str(start_year_option)
        start_date_query = " (STR_TO_DATE(T.start_date,'%%M %%Y') >= STR_TO_DATE('%s'" %(start_date) +",'%M %Y'))"

    if (start_year_before_option != '' and start_month_option != ''):
        start_date_before = str(start_month_before_option) + " " + str(start_year_before_option)
        start_date_query += " and (STR_TO_DATE(T.start_date,'%%M %%Y') <= STR_TO_DATE('%s'" %(start_date_before) +",'%M %Y'))"

    # check minimum_age, maximum_age and generate age_query

    minimum_age = 0
    maximum_age = 150

    if (minimum_age_option != ''):
        try:
            minimum_age = float(minimum_age_option)
        except TypeError:
            minimum_age = 0

    if (maximum_age_option != ''):
        try:
            maximum_age = float(maximum_age_option)
        except TypeError:
            maximum_age = 150

    #age_query = " (T.minimum_age_in_year >= %.9f" %float(minimum_age) +" and T.maximum_age_in_year <= %.9f)" %float(maximum_age)
    age_query = ("1=1")


    # check intervention model option and generate part of the query
    if (len(intervention_model_option) == 0):
        intervention_model_query = " (T.intervention_model LIKE '%%')"
    else:
        intervention_model_query += " ("
        for intervention_model in intervention_model_option:
            intervention_model_query += " T.intervention_model LIKE '%%%s%%'" %(intervention_model) + " OR"
            intervention_models += intervention_model + "/ "
        intervention_model_query += " T.intervention_model LIKE '%terminated%')"

    # check allocation option and generate part of the query
    if (len(allocation_option) == 0):
        allocation_query = " (T.allocation LIKE '%%')"
    else:
        allocation_query += " ("
        for allocation in allocation_option:
            allocation_query += " T.allocation LIKE '%%%s%%'" %(allocation) + " OR"
            allocations += allocation + "/ "
        allocation_query += " T.allocation LIKE '%terminated%')"

    # check time perspective option and generate part of the query
    if (len(time_perspective_option) == 0):
        #time_perspective_query = " (T.time_perspective LIKE '%%')"
        time_perspective_query = " (1=1)"
    else:
        time_perspective_query += " ("
        for time_perspective in time_perspective_option:
            time_perspective_query += " T.time_perspective LIKE '%%%s%%'" %(time_perspective) + " OR"
            time_perspectives += time_perspective + "/ "
        time_perspective_query += " T.time_perspective LIKE '%terminated%')"

    value_range_error = False
    if (lower != None and upper != None):
        try:
            if (float(lower) > float(upper)):
                value_range_verification_output += "Lower bound value cannot be greater than upper bound value."
                value_range_error = True
        except ValueError, e:
            print ("Value error")

    # get the total enrollment of trials contaning the variable in the certain range
    enrollment_value = 0


    # start the aggregate analysis

    # get the total number of trials meeting the requirements
    trials_meeting_requirement = ()

    if (lower != None and upper != None and value_range_error == False):

        # generate distribution of values in the certain range
        if width_option:
            width = 0
        else:
            if fixed_width is None:
                width = 0.5
            else:
                try:
                    width = float(fixed_width)
                except ValueError:
                    width = 0.5

        # get all expressions meeting user's query
        trials_exps = {}

        #cur = mysql.connect().cursor()




        #sql = "SELECT distinct V.TID, V.month FROM cancer_cui V, meta T where %s" %(curr_condition) + " and T.tid = V.tid and "+ phase_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query + " and "+ disease_query
        sql = "SELECT V.month, count(*), count(distinct V.tid) FROM cancer_cui V, meta T where %s" %(curr_condition) + " and T.tid = V.tid and "+ phase_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query + " and "+ disease_query + "and V.month>=%s and V.month <= %s" % (str(lower),str(upper)) + " group by month order by month"
        print sql

        cur.execute(sql)

        modal_boundary_output_simple = []
        modal_boundary_output_simple.append(["value", "Frequency"])
        num_of_trials = 0
        for row in cur.fetchall():
            modal_boundary_output_simple.append([str(row[0]),int(row[1])])
            num_of_trials += int(row[2])


        for row in cur.fetchall():
            id = row[0]
            #exps = ast.literal_eval(row[1])
            exps = row[1]
            if id in trials_exps:
                trials_exps[id] += exps  ####### some thing wrong
            else:
                trials_exps[id] = exps
            # get total number of trials meeting all the specifications
            if (len(trial_ids_with_conditions) > 0):
                if id in trial_ids_with_conditions:
                    trials_meeting_requirement += (id, )
            else:
                trials_meeting_requirement += (id, )

        trials_meeting_requirement = remove_duplicates(trials_meeting_requirement)

        #num_of_trials_output += "There are <span style=color:red>%s</span> studies "  % len(trials_meeting_requirement) + "meeting the requirements in the query <br><br>"
        num_of_trials_output += "There are <span style=color:red>%s</span> studies "  % (num_of_trials) + "meeting the requirements in the query <br><br>"

        # change the minimum_age and maximum_age to be null if they are not set:
        if (minimum_age == 0):
            minimum_age = ""
        if (maximum_age == 150):
            maximum_age = ""

        if (aggregate_analysis_type == 'Distribution of trials'):

            value_spectrum_output, value_spectrum_output_trial_ids = generate_value_spectrum_with_trialid(lower, upper, var, width, trials_exps, trial_ids_with_conditions, plot_type)

            if (len(phase_option) > 1 and width_option == False):
                # generating query for multiple phases
                multiple_phase_trials_exps = {}
                cur_num_phase = 0
                for phase in phase_option:
                    cur_num_phase += 1
                    multiple_phases_query = " (T.phase LIKE '%s') " % (phase)
                    multiple_phase_trials_exps = get_trials_exps(cur, var, multiple_phases_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query)

                    # generate multiple value spectrums for phases
                    single_phase_value_spectrum_output = []
                    single_phase_value_spectrum_output = generate_value_spectrum(lower, upper, var, width, multiple_phase_trials_exps, trial_ids_with_conditions, plot_type)
                    value_spectrum_output = combine_two_lists(value_spectrum_output, single_phase_value_spectrum_output, phase, cur_num_phase)

            csv_result.append(["Variable", "Number of qualifying trials", "lower bound", "upper bound", "condition", "phase", "status", "study type", "intervention type", "agency type", "gender", "minimum age", "maximum age", "start date after", "start date before", "intervention model", "allocation", "time perspective"])
            csv_result.append([var,str(len(trials_meeting_requirement)),lower,upper,condition_option,phases,statuses,study_types,intervention_types, agency_types, gender, str(minimum_age), str(maximum_age), start_date, start_date_before, intervention_models, allocations, time_perspectives])

            # output trial IDs with a specific value range


            csv_result.append(["Result of ","value spectrum with trial IDs"])
            try:
                for value, trialids in value_spectrum_output_trial_ids:
                    triallist = ""
                    for tid in trialids:
                        triallist += tid+";"
                    csv_result.append([str(value),str(triallist)])
                    #csv_result.append([value,trialids])
            except TypeError:
                print ("type error")


            if (width_option or len(phase_option) < 2):
                csv_result.append(["Result of ", "value spectrum"])
                #csv_result.append(["Value", "Number of Trials"])
                for value, num in value_spectrum_output:
                    csv_result.append([str(value),str(num)])

            if (len(phase_option) == 2 and width_option == False):
                csv_result.append(["Result of ", "value spectrum"])
                #csv_result.append(["Value", "Number of Trials"])
                for value, num1, num2, num3 in value_spectrum_output:
                    csv_result.append([str(value), str(num1), str(num2), str(num3)])

            if (len(phase_option) == 3 and width_option == False):
                csv_result.append(["Result of ", "value spectrum"])
                #csv_result.append(["Value", "Number of Trials"])
                for value, num1, num2, num3, num4 in value_spectrum_output:
                    csv_result.append([str(value), str(num1), str(num2), str(num3), str(num4)])

            if (len(phase_option) == 4 and width_option == False):
                csv_result.append(["Result of ", "value spectrum"])
                #csv_result.append(["Value", "Number of Trials"])
                for value, num1, num2, num3, num4, num5 in value_spectrum_output:
                    csv_result.append([str(value), str(num1), str(num2), str(num3), str(num4), str(num5)])

            if (len(phase_option) == 5 and width_option == False):
                csv_result.append(["Result of ", "value spectrum"])
                #csv_result.append(["Value", "Number of Trials"])
                for value, num1, num2, num3, num4, num5, num6 in value_spectrum_output:
                    csv_result.append([str(value), str(num1), str(num2), str(num3), str(num4), str(num5), str(num6)])

            if (len(phase_option) == 6 and width_option == False):
                csv_result.append(["Result of ", "value spectrum"])
                #csv_result.append(["Value", "Number of Trials"])
                for value, num1, num2, num3, num4, num5, num6, num7 in value_spectrum_output:
                    csv_result.append([str(value), str(num1), str(num2), str(num3), str(num4), str(num5), str(num6), str(num7)])

            display_csv_button = "Yes" if len(csv_result) > 0 else None

            if db.open:
                db.close()

            if (len(value_spectrum_output) > 0):
                return flask.render_template('study_counts.html', **locals())


        if (aggregate_analysis_type == 'Aggregated enrollment'):

            enrollment_type_query = " (T.enrollment_type LIKE '%%') "
            enrollment_spectrum_output, average_enrollment_spectrum_output = generate_enrollment_spectrum(cur, lower, upper, var, width, trials_exps, trial_ids_with_conditions, plot_type, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, enrollment_type_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query)

            if (len(phase_option) > 1 and width_option == False):
                # generating query for multiple phases
                multiple_phase_trials_exps = {}
                cur_num_phase = 0
                for phase in phase_option:
                    cur_num_phase += 1
                    multiple_phases_query = " (T.phase LIKE '%s') " % (phase)
                    multiple_phase_trials_exps = get_trials_exps(cur, var, multiple_phases_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query)

                    # generate multiple value spectrums for phases
                    single_phase_enrollment_spectrum_output = []
                    single_phase_average_enrollment_spectrum_output = []
                    single_phase_enrollment_spectrum_output, single_phase_average_enrollment_spectrum_output = generate_enrollment_spectrum(cur, lower, upper, var, width, multiple_phase_trials_exps, trial_ids_with_conditions, plot_type, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, enrollment_type_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query)
                    enrollment_spectrum_output = combine_two_lists(enrollment_spectrum_output, single_phase_enrollment_spectrum_output, phase, cur_num_phase)
                    average_enrollment_spectrum_output = combine_two_lists(average_enrollment_spectrum_output, single_phase_average_enrollment_spectrum_output, phase, cur_num_phase)

            csv_result.append(["Variable", "Number of qualifying trials", "lower bound", "upper bound", "condition", "phase", "status", "study type", "intervention type", "agency type", "gender", "minimum age", "maximum age", "start date after", "start date before", "intervention model", "allocation", "time perspective"])

            csv_result.append([var,str(len(trials_meeting_requirement)),lower,upper,condition_option,phases,statuses,study_types,intervention_types, agency_types, gender, str(minimum_age), str(maximum_age), start_date, start_date_before, intervention_models, allocations, time_perspectives])

            # CSV outout of aggregate enrollment

            if (width_option or len(phase_option) < 2):
                csv_result.append(["Result of ", "enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num in enrollment_spectrum_output:
                    csv_result.append([str(value),str(num)])

            if (len(phase_option) == 2 and width_option == False):
                csv_result.append(["Result of ", "enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3 in enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3)])

            if (len(phase_option) == 3 and width_option == False):
                csv_result.append(["Result of ", "enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4 in enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4)])

            if (len(phase_option) == 4 and width_option == False):
                csv_result.append(["Result of ", "enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4, num5 in enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4),str(num5)])

            if (len(phase_option) == 5 and width_option == False):
                csv_result.append(["Result of ", "enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4, num5, num6 in enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4),str(num5),str(num6)])

            if (len(phase_option) == 6 and width_option == False):
                csv_result.append(["Result of ", "enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4, num5, num6, num7 in enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4),str(num5),str(num6),str(num7)])

            # CSV output of average enrollment

            if (width_option or len(phase_option) < 2):
                csv_result.append(["Result of ", "average enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num in average_enrollment_spectrum_output:
                    csv_result.append([str(value),str(num)])

            if (len(phase_option) == 2 and width_option == False):
                csv_result.append(["Result of ", "average enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3 in average_enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3)])

            if (len(phase_option) == 3 and width_option == False):
                csv_result.append(["Result of ", "average enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4 in average_enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4)])

            if (len(phase_option) == 4 and width_option == False):
                csv_result.append(["Result of ", "average enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4, num5 in average_enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4),str(num5)])

            if (len(phase_option) == 5 and width_option == False):
                csv_result.append(["Result of ", "average enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4, num5, num6 in average_enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4),str(num5),str(num6)])

            if (len(phase_option) == 6 and width_option == False):
                csv_result.append(["Result of ", "average enrollment specturm"])
                #csv_result.append(["Value", "Enrollment"])
                for value, num1, num2, num3, num4, num5, num6, num7 in average_enrollment_spectrum_output:
                    csv_result.append([str(value),str(num1),str(num2),str(num3),str(num4),str(num5),str(num6),str(num7)])

            display_csv_button = "Yes" if len(csv_result) > 0 else None

            if db.open:
                db.close()

            if (len(enrollment_spectrum_output) > 0):
                return flask.render_template('enrollment.html', **locals())

        if (aggregate_analysis_type == 'Distribution of boundary values'):

            # modal_boundary_output,trials_upper_bound_output, trials_lower_bound_output = get_modal_boundary_value(var, trials_exps, trial_ids_with_conditions, upper, 0.1)
            #
            # value_range_distribution_output = get_value_range_distrbution(cur, var, trial_ids_with_conditions, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query)
            #
            # value_range_width_distribution_output = get_value_range_width_distrbution(cur, var, trial_ids_with_conditions, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query)
            #
            # csv_result.append(["Variable", "Number of qualifying trials", "lower bound", "upper bound", "condition", "phase", "status", "study type", "intervention type", "agency type", "gender", "minimum age", "maximum age", "start date after", "start date before", "intervention model", "allocation", "time perspective"])
            # csv_result.append([var,str(len(trials_meeting_requirement)),lower,upper,condition_option,phases,statuses,study_types,intervention_types, agency_types, gender, str(minimum_age), str(maximum_age), start_date, start_date_before, intervention_models, allocations, time_perspectives])
            #
            # csv_result.append(["Result of distribution of boundary values", ""])
            # for value, num1, num2, num3 in modal_boundary_output:
            #     csv_result.append([str(value),str(num1),str(num2),str(num3)])
            #
            # csv_result.append(["Boundary Value", "Studies using it as an upper bound"])
            # for value, trials in trials_upper_bound_output:
            #     triallist = ""
            #     for tid in trials:
            #         triallist += tid+";"
            #     csv_result.append([str(value),str(triallist)])
            #
            # csv_result.append(["Boundary Value", "Studies using it as a lower bound"])
            # for value, trials in trials_lower_bound_output:
            #     triallist = ""
            #     for tid in trials:
            #         triallist += tid+";"
            #     csv_result.append([str(value),str(triallist)])
            #
            # csv_result.append(["Result of distribution of permisslbe value ranges", ""])
            #
            # csv_result.append(["Value range", "Studies of studies"])
            # for value_intervals, number_of_trials in value_range_distribution_output:
            #     csv_result.append([str(value_intervals),str(number_of_trials)])
            #
            # csv_result.append(["Result of distribution of permisslbe value range widths", ""])
            #
            # csv_result.append(["Value range width", "Studies of studies"])
            # for value_interval_widths, number_of_trials in value_range_width_distribution_output:
            #     csv_result.append([str(value_interval_widths),str(number_of_trials)])
            #
            # display_csv_button = "Yes" if len(csv_result) > 0 else None
            #
            # if db.open:
            #     db.close()

            if (len(modal_boundary_output_simple) > 0):
                return flask.render_template('other_distributions.html', **locals())

    return "Builder page disease %s" %disease +"; and the variable is %s" %var


@application.route('/download', methods=['POST','GET'])
def download():
    def generate():
        for row in csv_result:
            yield ','.join(row) + '\n'
            #yield ','.join(row) + '\n'
            #for index in xrange(len(row)):
            #	yield row[index]

    return flask.Response(generate(), mimetype='text/csv',headers={"Content-Disposition": "attachment;filename=VATEC_output.csv"})


def generate_initial_value_spectrum(cur, cui,sty, disease):

    trials_exps = {}
    sql = "SELECT month, count(*) from cancer_cui where cui='%s' and sty = '%s' and task = '%s' and pattern != 'None' group by month order by month" % (cui,sty,disease)
    cur.execute(sql)

    output = []
    upper_value = 0
    lower_value = 0
    output.append(["value", "Frequency"])
    for row in cur.fetchall():
        output.append([str(row[0]),int(row[1])])
        if (int(row[0])>upper_value): upper_value = int(row[0])
        if (int(row[0])<lower_value):lower_value = int(row[0])

    # for row in cur.fetchall():
    #     id = row[0]
    #     exps = ast.literal_eval(row[1])
    #     if id in trials_exps:
    #         trials_exps[id] += exps
    #     else:
    #         trials_exps[id] = exps
    # # generate value points that exists for a variable
    # ranges = []
    #
    # for id, exps in trials_exps.iteritems():
    #     for exp in exps:
    #         ranges.append(float(exp[2]))
    #
    # ranges = remove_duplicates(ranges)
    # ranges = sorted(ranges)
    #
    # distributions = []
    # output = []
    # output.append(["value", "Frequency"])
    # trials_with_conditions = []
    #
    # distributions = get_value_spectrum_for_value_points(variable, ranges, trials_exps, trials_with_conditions)
    #
    # for i in (xrange(len(ranges))):
    #     output.append([ranges[i], len(distributions[i])])

    return (output, str(upper_value), str(lower_value))


# generate distribution for a range of values

def get_value_spectrum_for_value_points(variable, ranges, trials_exps, trials_with_conditions):
    # generate distribution
    distributions = []
    for range in ranges:
        range = float(range)
        #print "current range is %.1f" %(range)
        distrib = ()
        for id, exps in trials_exps.iteritems():
            #print "current expression is %s" %exps
            found, lowerB, lowerB_type, upperB, upperB_type = False, '', '', '', ''
            for exp in exps:
                if len(trials_with_conditions) == 0:
                    if (exp[0] == variable) and (exp[1]=='>' or exp[1]=='>='):
                        if lowerB =='' or float(exp[2]) < lowerB:
                            lowerB, lowerB_type = float(exp[2]), exp[1]
                            found = True
                    elif (exp[0] == variable) and (exp[1]=='<' or exp[1]=='<='):
                        if upperB =='' or float(exp[2]) > upperB:
                            upperB, upperB_type = float(exp[2]), exp[1]
                            found = True
                else:
                    if (id in trials_with_conditions):
                        if (exp[0] == variable) and (exp[1]=='>' or exp[1]=='>='):
                            if lowerB =='' or float(exp[2]) < lowerB:
                                lowerB, lowerB_type = float(exp[2]), exp[1]
                                found = True
                        elif (exp[0] == variable) and (exp[1]=='<' or exp[1]=='<='):
                            if upperB =='' or float(exp[2]) > upperB:
                                upperB, upperB_type = float(exp[2]), exp[1]
                                found = True
            if found:
                greater, greater_equal, lower, lower_equal = True,True,True,True
                if lowerB != '' and (lowerB_type == '>' and range <= lowerB):
                    greater = False
                elif lowerB != '' and (lowerB_type == '>=' and range < lowerB):
                    greater_equal = False
                elif upperB != '' and (upperB_type == '<' and range >= upperB):
                    lower = False
                elif upperB != '' and (upperB_type == '<=' and range > upperB):
                    lower_equal = False
                if (greater and greater_equal and lower and lower_equal):
                    distrib += (id, ) # add the trial id if it fit all conditions
        distributions.append(distrib) #add distrib for each range into final distribution
    return distributions

def generate_value_spectrum(lower, upper, variable, increment, trials_exps, trials_with_conditions, plot_type):

    ranges = []
    # generate equal width ranges with a fixed increment value
    if increment != 0:
        #ranges.append(float('-infinity'))
        thre = float(lower)
        while thre < (float(upper) + 0.001):
            ranges.append(thre)
            thre = thre + increment
            thre = math.ceil(thre*10)/10
    #ranges.append(float('infinity'))
    #print ranges
    else:
        # generate ranges of varying width

        ranges = get_varying_widths(variable, trials_exps, lower, upper, trials_with_conditions)

    distributions = []

    # output distribution
    output = []
    objects_list = []

    output.append(["value", "Total number of studies"])

    # generate distribution for value range (Zhe's code)
    if plot_type == "spectrum_for_ranges":
        distributions = get_value_spectrum_for_ranges(variable, ranges, trials_exps, trials_with_conditions)
        for i in (xrange(len(ranges)-1)):
            current_range = "%.1f" %(ranges[i]) + " - " + "%.1f" %(ranges[i+1])
            output.append([current_range, len(distributions[i])])
    else:
        distributions = get_value_spectrum_for_value_points(variable, ranges, trials_exps, trials_with_conditions)
        for i in (xrange(len(ranges))):
            output.append([ranges[i], len(distributions[i])])
    return output


def generate_value_spectrum_with_trialid(lower, upper, variable, increment, trials_exps, trials_with_conditions, plot_type):

    ranges = []
    # generate equal width ranges with a fixed increment value
    if increment != 0:
        #ranges.append(float('-infinity'))
        thre = float(lower)
        while thre < (float(upper) + 0.001):
            ranges.append(thre)
            thre = thre + increment
            thre = math.ceil(thre*10)/10
    #ranges.append(float('infinity'))
    #print ranges
    else:
        ranges = get_varying_widths(variable, trials_exps, lower, upper, trials_with_conditions)

    distributions = []

    # output distribution
    output = []
    objects_list = []
    trial_id_output = []

    output.append(["value", "Total number of studies"])

    # generate distribution for value range (Zhe's code)
    if plot_type == "spectrum_for_ranges":
        distributions = get_value_spectrum_for_ranges(variable, ranges, trials_exps, trials_with_conditions)
        for i in (xrange(len(ranges)-1)):
            current_range = "%.1f" %(ranges[i]) + " - " + "%.1f" %(ranges[i+1])
            output.append([current_range, len(distributions[i])])
            trial_id_output.append([current_range, distributions[i]])
    else:
        distributions = get_value_spectrum_for_value_points(variable, ranges, trials_exps, trials_with_conditions)
        for i in (xrange(len(ranges))):
            output.append([ranges[i], len(distributions[i])])
            trial_id_output.append([ranges[i], distributions[i]])
    return output, trial_id_output


# generate distribution for a range of values

def get_value_spectrum_for_ranges(variable, ranges, trials_exps, trials_with_conditions):
    # generate distribution
    distributions = []
    for i in (xrange(len(ranges)-1)):
        #print "currently processing range %.2f" %(ranges[i]) +" to %.2f" %(ranges[i+1])

        distrib = ()
        for id, exps in trials_exps.iteritems():
            found, lowerB, lowerB_type, upperB, upperB_type = False, '', '', '', ''
            for exp in exps:
                if len(trials_with_conditions) == 0:
                    if (exp[0] == variable) and (exp[1]=='>' or exp[1]=='>='):
                        if lowerB =='' or float(exp[2]) < lowerB:
                            lowerB, lowerB_type = float(exp[2]), exp[1]
                            found = True
                    elif (exp[0] == variable) and (exp[1]=='<' or exp[1]=='<='):
                        if upperB =='' or float(exp[2]) > upperB:
                            upperB, upperB_type = float(exp[2]), exp[1]
                            found = True
                else:
                    if (id in trials_with_conditions):
                        if (exp[0] == variable) and (exp[1]=='>' or exp[1]=='>='):
                            if lowerB =='' or exp[2] < lowerB:
                                lowerB, lowerB_type = float(exp[2]), exp[1]
                                found = True
                        elif (exp[0] == variable) and (exp[1]=='<' or exp[1]=='<='):
                            if upperB =='' or exp[2] > upperB:
                                upperB, upperB_type = float(exp[2]), exp[1]
                                found = True
            if found:
                case1, case2, case3, case4, case5, case6, case7, case8 = True, True, True, True, True, True, True, True
                # case1: lowerB: no, upperB: yes(symbol: <)
                #if lowerB == '' and upperB != '' and upperB_type == '<' and ranges[i] >= upperB:
                if lowerB == '' and upperB != '' and upperB_type == '<' and (ranges[i] >= upperB or ranges[i+1] >= upperB):
                    case1 = False
                # case2: lowerB: no, upperB: yes(symbol: <=)
                #if lowerB == '' and upperB != '' and upperB_type == '<=' and ranges[i] > upperB:
                if lowerB == '' and upperB != '' and upperB_type == '<=' and (ranges[i] > upperB or ranges[i+1] > upperB):
                    case2 = False
                # case3: lowerB: yes(symbol: >), upperB: no
                #if lowerB != '' and upperB == '' and lowerB_type == '>' and ranges[i+1] <= lowerB:
                if lowerB != '' and upperB == '' and lowerB_type == '>' and (ranges[i+1] <= lowerB or ranges[i] <= lowerB):
                    case3 = False
                # case4: lowerB: yes(symbol: >=), upperB: no
                #if lowerB != '' and upperB == '' and lowerB_type == '>=' and ranges[i+1] < lowerB:
                if lowerB != '' and upperB == '' and lowerB_type == '>=' and (ranges[i+1] < lowerB or ranges[i] < lowerB):
                    case4 = False
                # case5: lowerB: yes(symbol: >), upperB: yes(symbol: <)
                #if lowerB != '' and upperB != '' and lowerB_type == '>' and upperB_type == '<' and (ranges[i+1] <= lowerB or ranges[i] >= upperB):
                if lowerB != '' and upperB != '' and lowerB_type == '>' and upperB_type == '<' and (ranges[i+1] <= lowerB or ranges[i] >= upperB or ranges[i] <= lowerB or ranges[i+1] >= upperB):
                    case5 = False
                # case6: lowerB: yes(symbol: >=), upperB: yes(symbol: <)
                #if lowerB != '' and upperB != '' and lowerB_type == '>=' and upperB_type == '<' and (ranges[i+1] < lowerB or ranges[i] >= upperB):
                if lowerB != '' and upperB != '' and lowerB_type == '>=' and upperB_type == '<' and (ranges[i+1] < lowerB or ranges[i] >= upperB or ranges[i] < lowerB or ranges[i+1] >= upperB):
                    case6 = False
                # case7: lowerB: yes(symbol: >), upperB: yes(symbol: <=)
                #if lowerB != '' and upperB != '' and lowerB_type == '>' and upperB_type == '<=' and (ranges[i+1] <= lowerB or ranges[i] > upperB):
                if lowerB != '' and upperB != '' and lowerB_type == '>' and upperB_type == '<=' and (ranges[i+1] <= lowerB or ranges[i] >= upperB or ranges[i] <= lowerB or ranges[i+1] > upperB):
                    case7 = False
                # case8: lowerB: yes(symbol: >=), upperB: yes(symbol: <=)
                #if lowerB != '' and upperB != '' and lowerB_type == '>=' and upperB_type == '<=' and (ranges[i+1] < lowerB or ranges[i] > upperB):
                if lowerB != '' and upperB != '' and lowerB_type == '>=' and upperB_type == '<=' and (ranges[i+1] <= lowerB or ranges[i] >= upperB or ranges[i] < lowerB or ranges[i+1] > upperB):
                    case8 = False
                if (case1 and case2 and case3 and case4 and case5 and case6 and case7 and case8):
                    distrib += (id, ) # add the trial ID if it fit all the conditions
        distributions.append(distrib) #add distrib for each range into final distribution
    return distributions



def generate_enrollment_spectrum(cur, lower, upper, variable, increment, trials_exps, trials_with_conditions, plot_type, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, enrollment_type_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query):

    ranges = []

    # generate equal width ranges with a fixed increment value
    if increment != 0:
        #ranges.append(float('-infinity'))
        thre = float(lower)
        while thre < (float(upper) + 0.001):
            ranges.append(thre)
            thre = thre + increment
            thre = math.ceil(thre*10)/10
    #ranges.append(float('infinity'))
    #print ranges
    else:
        # generate ranges of varying width
        ranges = get_varying_widths(variable, trials_exps, lower, upper, trials_with_conditions)

    distributions = []

    # output for aggregated enrollment
    output = []
    output.append(["value", "Total enrollment"])

    # output for average enrollment
    average_output = []
    average_output.append(["value", "Average enrollment"])


    # get all enrollment for matching trials
    trials_enrollment = {}
    #cur = mysql.connect().cursor()

    #db = mysql.connect()
    #cur = db.cursor()

    sql = "SELECT distinct T.TID, T.enrollment FROM meta T where "+ phase_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ enrollment_type_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query + " and " + disease_query
    #print "query used: %s" %(sql)

    cur.execute(sql)
    for row in cur.fetchall():
        id = row[0]
        enrollment = row[1]
        try:
            enrollment_value = int(enrollment)
        except ValueError:
            enrollment_value = 0
        trials_enrollment[id] = enrollment_value


    # generate distribution for value range (Zhe's code)
    if plot_type == "spectrum_for_ranges":
        distributions = get_value_spectrum_for_ranges(variable, ranges, trials_exps, trials_with_conditions)
        for i in (xrange(len(ranges)-1)):
            current_range = "%.1f" %(ranges[i]) + " - " + "%.1f" %(ranges[i+1])
            total_enrollment = 0
            for trial_id in distributions[i]:
                total_enrollment += trials_enrollment[trial_id]
            #output.append([current_range, len(distributions[i])])
            output.append([current_range, total_enrollment])
            if (len(distributions[i]) != 0):
                average_enrollment = total_enrollment/len(distributions[i])
                average_enrollment = math.ceil(average_enrollment)/1
            else:
                average_enrollment = 0
            average_output.append([current_range, average_enrollment])
    else:
        distributions = get_value_spectrum_for_value_points(variable, ranges, trials_exps, trials_with_conditions)
        for i in (xrange(len(ranges))):
            total_enrollment = 0
            for trial_id in distributions[i]:
                total_enrollment += trials_enrollment[trial_id]
            #output.append([ranges[i], len(distributions[i])])
            output.append([ranges[i], total_enrollment])
            if (len(distributions[i]) != 0):
                average_enrollment = total_enrollment/len(distributions[i])
                average_enrollment = math.ceil(average_enrollment)/1
            else:
                average_enrollment = 0
            average_output.append([ranges[i], average_enrollment])

    #if db.open:
    #	db.close()

    return output, average_output



# get modal boundary value distribution

def get_modal_boundary_value(var, trials_exps, trials_with_conditions, max, interval):

    # get distribution of trials with a certain boundary value
    boundary_value = 0
    output = []
    output.append(["value", "Number of studies", "Number of studies with Lower bound", "Number of studies with Upper bound"])

    # trials using upper bounds
    trials_upper_bound_output =[]
    # trials using lower bounds
    trials_lower_bound_output = []

    while boundary_value < (float(max) + 0.0001):
        fitting_trials = get_fitting_trials(var, trials_exps, trials_with_conditions, float(boundary_value))
        fitting_trials_lower_bound = get_trials_with_lower_bound(var, trials_exps, trials_with_conditions, float(boundary_value))
        fitting_trials_upper_bound = get_trials_with_upper_bound(var, trials_exps, trials_with_conditions, float(boundary_value))

        output.append([boundary_value, len(fitting_trials), len(fitting_trials_lower_bound), len(fitting_trials_upper_bound)])
        trials_upper_bound_output.append([boundary_value,fitting_trials_upper_bound])
        trials_lower_bound_output.append([boundary_value,fitting_trials_lower_bound])


        boundary_value += interval
        boundary_value = math.ceil(boundary_value*10)/10

    return output,trials_upper_bound_output,trials_lower_bound_output


# what trials are using a specific boundary HbA1c value to determine the eligibility of patients?

def get_fitting_trials(variable, trials_exps, trials_with_conditions, boundary_value):

    fitting_trials = ()
    for id, exps in trials_exps.iteritems():
        found = False
        for exp in exps:
            if (len(trials_with_conditions) == 0):
                if exp[0] == variable:
                    if ((exp[1] in ['>','>=','<','<=']) and (float(exp[2]) == boundary_value)):
                        found = True
            else:
                if (exp[0] == variable) and (id in trials_with_conditions):
                    if ((exp[1] in ['>','>=','<','<=']) and (float(exp[2]) == boundary_value)):
                        found = True
        if found:
            fitting_trials += (id, )

    fitting_trials = remove_duplicates(fitting_trials)

    return fitting_trials


# what trials are using a specific lower boundary HbA1c value to determine the eligibility of patients?

def get_trials_with_lower_bound(variable, trials_exps, trials_with_conditions, boundary_value):

    fitting_trials = ()
    for id, exps in trials_exps.iteritems():
        found = False
        for exp in exps:
            if (len(trials_with_conditions) == 0):
                if exp[0] == variable:
                    if ((exp[1] in ['>','>=']) and (float(exp[2]) == boundary_value)):
                        found = True
            else:
                if (exp[0] == variable) and (id in trials_with_conditions):
                    if ((exp[1] in ['>','>=']) and (float(exp[2]) == boundary_value)):
                        found = True
        if found:
            fitting_trials += (id, )

    fitting_trials = remove_duplicates(fitting_trials)

    return fitting_trials


# what trials are using a specific upper boundary HbA1c value to determine the eligibility of patients?

def get_trials_with_upper_bound(variable, trials_exps, trials_with_conditions, boundary_value):

    fitting_trials = ()
    for id, exps in trials_exps.iteritems():
        found = False
        for exp in exps:
            if (len(trials_with_conditions) == 0):
                if exp[0] == variable:
                    if ((exp[1] in ['<','<=']) and (float(exp[2]) == boundary_value)):
                        found = True
            else:
                if (exp[0] == variable) and (id in trials_with_conditions):
                    if ((exp[1] in ['<','<=']) and (float(exp[2]) == boundary_value)):
                        found = True
        if found:
            fitting_trials += (id, )

    fitting_trials = remove_duplicates(fitting_trials)

    return fitting_trials

# get value range distributions

def get_value_range_distrbution(cur, variable, trials_with_conditions, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query):

    #cur = mysql.connect().cursor()
    #db = mysql.connect()
    #cur = db.cursor()


    sql = "select  tid, min, max, symbol_min,symbol_max from numeric_features where tid in (SELECT T.TID FROM meta T where "+ phase_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query + " and " + disease_query +") and numeric_feature = '%s'" %(variable) +" and min < max and (equal_value = 'n/a' or (equal_value != 'n/a' and not (min='-inf' and max='inf')))"

    cur.execute(sql)

    value_range_dict = {}

    for row in cur.fetchall():
        #range = row[1] + "-"+ row[2]

        range = str(get_range(row[1],row[2],row[3],row[4]))

        if (len(trials_with_conditions) > 0):
            if (row[0] in trials_with_conditions):
                if range in value_range_dict:
                    value_range_dict[range] += 1
                else:
                    value_range_dict[range] = 1
        else:
            if range in value_range_dict:
                value_range_dict[range] += 1
            else:
                value_range_dict[range] = 1
    sorted_value_range_dict = sorted(value_range_dict.iteritems(), key=itemgetter(1), reverse=True)

    output = []
    output.append(["Value range", "Number of studies"])

    for r, v in sorted_value_range_dict:
        output.append([r,v])

    #if db.open:
    #	db.close()

    return output


def get_range(min,max,symbol_min,symbol_max):

    range = ''

    if (symbol_min == '>=' and min != '-inf'):
        symbol_min = '['
    else:
        symbol_min = '('

    if (symbol_max == '<=' and max != 'inf'):
        symbol_max = ']'
    else:
        symbol_max = ')'

    range = symbol_min + min +"," + max + symbol_max

    '''
        if (min == '-inf'):
        range = symbol_max + max
        elif (max == 'inf'):
        range = symbol_min + min
        else:
        range = symbol_min + min +","+ symbol_max + max
        '''

    return range


# get value range distributions

def get_value_range_width_distrbution(cur, variable, trials_with_conditions, phase_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query, intervention_model_query, allocation_query, time_perspective_query, disease_query):

    #cur = mysql.connect().cursor()
    #db = mysql.connect()
    #cur = db.cursor()

    sql = "select  tid, min, max from numeric_features where tid in (SELECT T.TID FROM meta T where "+ phase_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query + " and "+ disease_query +") and numeric_feature = '%s'" %(variable) +" and min < max and equal_value = 'n/a' and min !='-inf' and max != 'inf'"

    cur.execute(sql)

    value_range_width_dict = {}

    for row in cur.fetchall():
        width = float(row[2]) - float(row[1])
        width = math.ceil(width*10)/10
        if (width > 0):
            if (len(trials_with_conditions) > 0):
                if (row[0] in trials_with_conditions):
                    if width in value_range_width_dict:
                        value_range_width_dict[width] += 1
                    else:
                        value_range_width_dict[width] = 1
            else:
                if width in value_range_width_dict:
                    value_range_width_dict[width] += 1
                else:
                    value_range_width_dict[width] = 1
    #sorted_value_range_width_dict = sorted(value_range_width_dict.iteritems(), key=itemgetter(0), reverse=False)

    output = []
    output.append(["Value range width", "Number of studies"])

    for k in sorted(value_range_width_dict):
        output.append([math.ceil(k*10)/10,value_range_width_dict[k]])

    #if db.open:
    #	db.close()

    return output



def get_trials_exps(cur, var, phases_query, status_query, study_type_query, intervention_type_query, agency_type_query, gender_query, start_date_query, age_query,  intervention_model_query, allocation_query, time_perspective_query, disease_query):

    trials_exps = {}
    #cur = mysql.connect().cursor()
    #db = mysql.connect()
    #cur = db.cursor()

    #cur = db.cursor()

    sql = "SELECT distinct V.TID, V.expressions FROM numeric_features V, meta T where V.numeric_feature = '%s'" %var + " and T.tid = V.tid and "+ phases_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query + " and "+ disease_query

    # for the cancer CEF paper

    #sql = "SELECT distinct V.TID, V.expressions FROM numeric_features V, trials T where V.numeric_feature = '%s'" %var + " and T.tid in (select distinct tid from cancer_trials) and  T.tid = V.tid and "+ phases_query + " and "+ status_query + " and "+ study_type_query + " and "+ intervention_type_query + " and "+ agency_type_query + " and "+ gender_query + " and "+ start_date_query + " and "+ age_query + " and "+ intervention_model_query + " and "+ allocation_query + " and "+ time_perspective_query

    cur.execute(sql)
    for row in cur.fetchall():
        id = row[0]
        exps = ast.literal_eval(row[1])
        if id in trials_exps:
            trials_exps[id] += exps
        else:
            trials_exps[id] = exps

    #if db.open:
    #	db.close()

    return trials_exps


# generate widths of varying length

def get_varying_widths (variable,trials_exps,lower, upper, trials_with_conditions):

    ranges = []
    for id, exps in trials_exps.iteritems():
        for exp in exps:
            if len(trials_with_conditions) == 0:
                if (exp[0] == variable) and (float(exp[2]) >= float(lower)) and (float(exp[2]) <= float(upper)):
                    ranges.append(float(exp[2]))
            else:
                if (id in trials_with_conditions):
                    if (exp[0] == variable) and (float(exp[2]) >= float(lower)) and (float(exp[2]) <= float(upper)):
                        ranges.append(float(exp[2]))
    ranges = remove_duplicates(ranges)
    ranges = sorted(ranges)
    return ranges


def remove_duplicates(l):
    return list(set(l))

# get the html source associated to a URL
def download_web_data (url):
    try:
        req = urllib2.Request (url, headers={'User-Agent':"Magic Browser"})
        con = urllib2.urlopen(req)
        html = con.read()
        con.close()
        return html
    except Exception as e:
        print ('%s: %s' % (e, url))
        return None

# Find Trial IDs of a specific disease

def get_disease_clinical_trials (disease):
    # base url
    url = 'http://clinicaltrials.gov/search?cond=' + disease.replace(' ', '+') + '&displayxml=true&count='
    print("url is %s") %url
    # get the number of studies available (request 0 studies as result)
    xmltree = xml_parser.fromstring (download_web_data('%s%s' % (url, '0')))
    nnct = xmltree.get('count')
    # get the list of clinical studies
    xmltree = xml_parser.fromstring (download_web_data('%s%s' % (url, nnct)))
    lnct = xmltree.findall ('clinical_study')

    #criterias = []
    trial_ids = []

    url_trial = 'http://clinicaltrials.gov/show/%s?displayxml=true'
    for nct in lnct:
        ids = nct.find ('nct_id')
        if ids is not None:
            trial_ids.append((ids.text))
        else:
            print 'no id'

    return trial_ids


def combine_two_lists (list1, list2, phase, cur_num_phase):

    output = []

    if (cur_num_phase == 1):
        output.append([list1[0][0], list1[0][1], str(phase)])
        for i in xrange(len(list1) - 1):
            output.append([list1[i+1][0], list1[i+1][1], list2[i+1][1]])

    if (cur_num_phase == 2):
        output.append([list1[0][0], list1[0][1], list1[0][2], str(phase)])
        for i in xrange(len(list1) - 1):
            output.append([list1[i+1][0], list1[i+1][1], list1[i+1][2], list2[i+1][1]])

    if (cur_num_phase == 3):
        output.append([list1[0][0], list1[0][1], list1[0][2], list1[0][3], str(phase)])
        for i in xrange(len(list1) - 1):
            output.append([list1[i+1][0], list1[i+1][1], list1[i+1][2], list1[i+1][3], list2[i+1][1]])

    if (cur_num_phase == 4):
        output.append([list1[0][0], list1[0][1], list1[0][2], list1[0][3], list1[0][4], str(phase)])
        for i in xrange(len(list1) - 1):
            output.append([list1[i+1][0], list1[i+1][1], list1[i+1][2], list1[i+1][3], list1[i+1][4], list2[i+1][1]])

    if (cur_num_phase == 5):
        output.append([list1[0][0], list1[0][1], list1[0][2], list1[0][3], list1[0][4], list1[0][5], str(phase)])
        for i in xrange(len(list1) - 1):
            output.append([list1[i+1][0], list1[i+1][1], list1[i+1][2], list1[i+1][3], list1[i+1][4], list1[i+1][5], list2[i+1][1]])

    if (cur_num_phase == 6):
        output.append([list1[0][0], list1[0][1], list1[0][2], list1[0][3], list1[0][4], list1[0][5], list1[0][6], str(phase)])
        for i in xrange(len(list1) - 1):
            output.append([list1[i+1][0], list1[i+1][1], list1[i+1][2], list1[i+1][3], list1[i+1][4], list1[i+1][5], list1[i+1][6], list2[i+1][1]])

    return output



if __name__ == '__main__':
    application.run(host='0.0.0.0', debug=True)
