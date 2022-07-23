#ALL_GIT_FILES = ${shell git ls-files}
SRC_FILES = Makefile ${shell find $$(pwd) -type d -name target -prune -o -type d -name '.*' -prune -o -type f -print}

all : .staged;

.staged : ${SRC_FILES}
	@echo "changed:"
	@for i in ${sort $?}; do echo "    $$i"; done
	./sbt --batch stage
	touch .staged

clean: sbt_clean
	@rm -vrf .staged

sbt_%:
	./sbt --batch $*

% : sbt_%;
