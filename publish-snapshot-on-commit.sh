set -eu

if [ "$TRAVIS_REPO_SLUG" == "google/volley" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing snapshot build to OJO...\n"

  ./gradlew artifactoryPublish

  echo -e "Published snapshot build to OJO"
else
  echo -e "Not publishing snapshot"
fi