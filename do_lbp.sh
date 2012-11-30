RUNTMP=./run_tmp

./compile.sh LBP

rm -rf $RUNTMP
mkdir -p $RUNTMP/lbp_edge
cp lbp.edge $RUNTMP/lbp_edge/

mkdir -p $RUNTMP/lbp_prior
cp lbp.prior $RUNTMP/lbp_prior/

./run.sh LBP $RUNTMP/lbp_edge $RUNTMP/lbp_prior $RUNTMP/lbp_output $1 1 $2 PRE$3

rm -rf dd_deg_count
rm -rf dd_node_deg
rm -rf temp_matvecnaive_ssrun_tmp
rm -rf lbp_m_path
rm -rf lbp_cur_vector
