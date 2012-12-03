all: paper.pdf
	./do_bp.sh 5 3 4

paper.pdf:
	./make_doc.sh

clean:
	./clean_doc.sh
	rm -rf run_tmp
	rm -f *.jar

spotless: clean
	\rm -f $(MAIN).ps $(MAIN).pdf
	\rm -rf TST
	\rm -f all.tar
	./clean_doc.sh

all.tar:
	tar jcfh all.tar.bz2 ./

