RUNTMP=./run_tmp

./compile.sh SBP

rm -rf $RUNTMP
mkdir -p $RUNTMP/bp_edge
cp bp.edge $RUNTMP/bp_edge/

mkdir -p $RUNTMP/bp_prior
cp bp.prior $RUNTMP/bp_prior/

./run.sh SBP $RUNTMP/bp_edge $RUNTMP/bp_prior $RUNTMP/bp_output $3 3 $1 makesym $2 bp.ep newmsg $4
